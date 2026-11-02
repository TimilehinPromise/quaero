package org.timmy.knowcommute.figure;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;
import javax.xml.XMLConstants;
import javax.xml.parsers.SAXParserFactory;
import org.apache.batik.anim.dom.SAXSVGDocumentFactory;
import org.apache.batik.bridge.UserAgent;
import org.apache.batik.bridge.UserAgentAdapter;
import org.apache.batik.transcoder.SVGAbstractTranscoder;
import org.apache.batik.transcoder.TranscoderException;
import org.apache.batik.transcoder.TranscoderInput;
import org.apache.batik.transcoder.TranscoderOutput;
import org.apache.batik.transcoder.image.PNGTranscoder;
import org.apache.batik.util.ParsedURL;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.timmy.knowcommute.extraction.FetchedPage;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.XMLReader;

@Component
public class SvgRasterizer {

    private static final float MAX_DIMENSION = 4096;
    private static final long MAX_PIXELS = 16_000_000;
    private static final float DEFAULT_DIMENSION = 400;
    private static final Pattern CSS_URL = Pattern.compile("url\\s*\\(([^)]+)\\)", Pattern.CASE_INSENSITIVE);

    private final FigureProperties properties;

    public SvgRasterizer(FigureProperties properties) {
        this.properties = properties;
    }

    public FetchedPage rasterize(FetchedPage svg) {
        if (svg.body().length > properties.maxResponseBytes()) {
            throw new SvgRasterizationException("SVG exceeds the input byte limit");
        }
        rejectDoctype(svg.body());
        try {
            Document document = parse(svg.body());
            rejectActiveOrExternalContent(document);
            Dimensions dimensions = dimensions(document.getDocumentElement());
            byte[] png = transcode(document, dimensions);
            return new FetchedPage(svg.finalUrl(), MediaType.IMAGE_PNG, png);
        } catch (Exception e) {
            SvgRasterizationException rasterizationFailure = findRasterizationFailure(e);
            if (rasterizationFailure != null) {
                throw rasterizationFailure;
            }
            throw new SvgRasterizationException("Could not rasterize SVG", e);
        }
    }

    private void rejectDoctype(byte[] body) {
        String markup = new String(body, java.nio.charset.StandardCharsets.UTF_8).toLowerCase(Locale.ROOT);
        if (markup.contains("<!doctype") || markup.contains("<!entity")) {
            throw new SvgRasterizationException("SVG doctype and entity declarations are not allowed");
        }
    }

    private SvgRasterizationException findRasterizationFailure(Throwable failure) {
        Throwable current = failure;
        while (current != null) {
            if (current instanceof SvgRasterizationException rasterizationException) {
                return rasterizationException;
            }
            current = current.getCause();
        }
        return null;
    }

    private Document parse(byte[] body) throws Exception {
        SecureSaxDocumentFactory factory = new SecureSaxDocumentFactory();
        Document document = factory.createSVGDocument("about:blank", new ByteArrayInputStream(body));
        Element root = document.getDocumentElement();
        if (root == null || !"svg".equals(root.getLocalName())) {
            throw new SvgRasterizationException("Document is not an SVG image");
        }
        rejectExternalStylesheets(document);
        return document;
    }

    private static XMLReader secureXmlReader() throws Exception {
        SAXParserFactory factory = SAXParserFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setXIncludeAware(false);
        factory.setValidating(false);
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        XMLReader reader = factory.newSAXParser().getXMLReader();
        reader.setFeature("http://xml.org/sax/features/external-general-entities", false);
        reader.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        reader.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        reader.setEntityResolver((publicId, systemId) -> {
            throw new SvgRasterizationException("External SVG entities are not allowed");
        });
        return reader;
    }

    private void rejectActiveOrExternalContent(Document document) {
        NodeList elements = document.getElementsByTagName("*");
        for (int index = 0; index < elements.getLength(); index++) {
            Element element = (Element) elements.item(index);
            String tag = element.getLocalName().toLowerCase(Locale.ROOT);
            if ("script".equals(tag) || "foreignobject".equals(tag)) {
                throw new SvgRasterizationException("Active SVG content is not allowed");
            }
            NamedNodeMap attributes = element.getAttributes();
            for (int attributeIndex = 0; attributeIndex < attributes.getLength(); attributeIndex++) {
                Node attribute = attributes.item(attributeIndex);
                String name = attribute.getNodeName().toLowerCase(Locale.ROOT);
                String value = attribute.getNodeValue().trim();
                if (name.startsWith("on")) {
                    throw new SvgRasterizationException("SVG event handlers are not allowed");
                }
                if (("href".equals(name) || name.endsWith(":href") || "src".equals(name))
                        && !value.startsWith("#")) {
                    throw new SvgRasterizationException("External SVG resources are not allowed");
                }
                if ("style".equals(name)) {
                    rejectExternalCss(value);
                }
            }
            if ("style".equals(tag)) {
                rejectExternalCss(element.getTextContent());
            }
        }
    }

    private void rejectExternalStylesheets(Document document) {
        NodeList children = document.getChildNodes();
        for (int index = 0; index < children.getLength(); index++) {
            Node child = children.item(index);
            if (child.getNodeType() == Node.PROCESSING_INSTRUCTION_NODE) {
                throw new SvgRasterizationException("External SVG stylesheets are not allowed");
            }
        }
    }

    private void rejectExternalCss(String css) {
        if (css.toLowerCase(Locale.ROOT).contains("@import")) {
            throw new SvgRasterizationException("External SVG stylesheets are not allowed");
        }
        Matcher matcher = CSS_URL.matcher(css);
        while (matcher.find()) {
            String target = matcher.group(1).trim().replaceAll("^['\"]|['\"]$", "");
            if (!target.startsWith("#") && !target.startsWith("data:font/")) {
                throw new SvgRasterizationException("External SVG resources are not allowed");
            }
        }
    }

    private Dimensions dimensions(Element root) {
        float[] viewBox = viewBox(root.getAttribute("viewBox"));
        Float width = length(root.getAttribute("width"));
        Float height = length(root.getAttribute("height"));
        if (width == null && height == null) {
            width = viewBox == null ? DEFAULT_DIMENSION : viewBox[2];
            height = viewBox == null ? DEFAULT_DIMENSION : viewBox[3];
        } else if (width == null) {
            width = viewBox == null ? height : height * viewBox[2] / viewBox[3];
        } else if (height == null) {
            height = viewBox == null ? width : width * viewBox[3] / viewBox[2];
        }
        if (!validDimension(width) || !validDimension(height)
                || width > MAX_DIMENSION || height > MAX_DIMENSION
                || (long) Math.ceil(width) * (long) Math.ceil(height) > MAX_PIXELS) {
            throw new SvgRasterizationException("SVG dimensions exceed the rasterization limit");
        }
        return new Dimensions(width, height);
    }

    private float[] viewBox(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String[] parts = value.trim().split("[\\s,]+");
        if (parts.length != 4) {
            return null;
        }
        try {
            float[] viewBox = new float[4];
            for (int index = 0; index < parts.length; index++) {
                viewBox[index] = Float.parseFloat(parts[index]);
            }
            return validDimension(viewBox[2]) && validDimension(viewBox[3]) ? viewBox : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Float length(String value) {
        if (value == null || !value.matches("\\s*\\d+(?:\\.\\d+)?(?:px)?\\s*")) {
            return null;
        }
        try {
            return Float.parseFloat(value.trim().replaceFirst("px$", ""));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private boolean validDimension(float value) {
        return Float.isFinite(value) && value > 0;
    }

    private byte[] transcode(Document document, Dimensions dimensions) throws Exception {
        PNGTranscoder transcoder = new SecurePngTranscoder();
        transcoder.addTranscodingHint(SVGAbstractTranscoder.KEY_WIDTH, dimensions.width());
        transcoder.addTranscodingHint(SVGAbstractTranscoder.KEY_HEIGHT, dimensions.height());
        transcoder.addTranscodingHint(SVGAbstractTranscoder.KEY_EXECUTE_ONLOAD, false);
        transcoder.addTranscodingHint(SVGAbstractTranscoder.KEY_ALLOWED_SCRIPT_TYPES, "");
        transcoder.addTranscodingHint(SVGAbstractTranscoder.KEY_ALLOW_EXTERNAL_RESOURCES, false);
        transcoder.addTranscodingHint(SVGAbstractTranscoder.KEY_CONSTRAIN_SCRIPT_ORIGIN, true);

        LimitedByteArrayOutputStream output = new LimitedByteArrayOutputStream(properties.maxResponseBytes());
        transcoder.transcode(new TranscoderInput(document), new TranscoderOutput(output));
        return output.toByteArray();
    }

    private static final class SecurePngTranscoder extends PNGTranscoder {
        @Override
        protected UserAgent createUserAgent() {
            return new NoExternalResourceUserAgent();
        }

        @Override
        public void writeImage(BufferedImage image, TranscoderOutput output) throws TranscoderException {
            try {
                if (!ImageIO.write(image, "png", output.getOutputStream())) {
                    throw new TranscoderException("No PNG writer is available");
                }
            } catch (IOException e) {
                throw new TranscoderException(e);
            }
        }
    }

    private static final class SecureSaxDocumentFactory extends SAXSVGDocumentFactory {
        private SecureSaxDocumentFactory() {
            super("com.sun.org.apache.xerces.internal.parsers.SAXParser");
        }

        @Override
        protected Document createDocument(InputSource source) throws IOException {
            try {
                parser = secureXmlReader();
            } catch (Exception e) {
                throw new IOException("Could not create the secure SVG parser", e);
            }
            return super.createDocument(source);
        }
    }

    private static final class NoExternalResourceUserAgent extends UserAgentAdapter {
        @Override
        public void checkLoadScript(String type, ParsedURL scriptURL, ParsedURL docURL) {
            throw new SecurityException("SVG scripts are disabled");
        }

        @Override
        public void checkLoadExternalResource(ParsedURL resourceURL, ParsedURL docURL) {
            String url = resourceURL == null ? "" : resourceURL.toString();
            if (!url.startsWith("data:font/")) {
                throw new SecurityException("External SVG resources are disabled");
            }
        }
    }

    private record Dimensions(float width, float height) {
    }

    private static final class LimitedByteArrayOutputStream extends ByteArrayOutputStream {
        private final int limit;

        private LimitedByteArrayOutputStream(int limit) {
            this.limit = limit;
        }

        @Override
        public synchronized void write(int value) {
            check(1);
            super.write(value);
        }

        @Override
        public synchronized void write(byte[] bytes, int offset, int length) {
            check(length);
            super.write(bytes, offset, length);
        }

        private void check(int additionalBytes) {
            if (size() + additionalBytes > limit) {
                throw new SvgRasterizationException("Rasterized PNG exceeds the output byte limit");
            }
        }
    }
}
