package com.dailyrupi.prreview.tools;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

/**
 * Reads tool reports as XML without ever resolving external entities or DTDs. Reports come from the PR's own
 * build, so they are untrusted input, and JaCoCo's report.dtd must not be fetched over the network.
 */
final class XmlDocuments {

    private XmlDocuments() {
    }

    static Document parse(Path file) throws IOException {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            DocumentBuilder builder = factory.newDocumentBuilder();
            builder.setEntityResolver((publicId, systemId) -> new InputSource(new StringReader("")));
            builder.setErrorHandler(null);
            try (InputStream in = Files.newInputStream(file)) {
                return builder.parse(in);
            }
        } catch (ParserConfigurationException | SAXException e) {
            throw new IOException("Cannot read " + file.getFileName() + ": " + e.getMessage(), e);
        }
    }

    /** Direct children of {@code parent} named {@code tag}, in document order. */
    static List<Element> children(Element parent, String tag) {
        List<Element> found = new ArrayList<>();
        for (Node node = parent.getFirstChild(); node != null; node = node.getNextSibling()) {
            if (node instanceof Element element && element.getTagName().equals(tag)) {
                found.add(element);
            }
        }
        return found;
    }

    static Optional<Element> child(Element parent, String tag) {
        return children(parent, tag).stream().findFirst();
    }

    static String childText(Element parent, String tag) {
        return child(parent, tag).map(Element::getTextContent).map(String::trim).orElse(null);
    }

    static String attr(Element element, String name) {
        String value = element.getAttribute(name);
        return value.isEmpty() ? null : value;
    }

    static Integer intAttr(Element element, String name) {
        String value = attr(element, name);
        if (value == null) {
            return null;
        }
        try {
            return Integer.valueOf(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
