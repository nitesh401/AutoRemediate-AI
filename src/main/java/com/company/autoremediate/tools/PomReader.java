package com.company.autoremediate.tools;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/** Minimal, XXE-safe pom.xml reader (no Maven resolution). */
public final class PomReader {
    private PomReader() {}

    public record PomInfo(String groupId, String artifactId, String version,
                          String parentArtifactId, String parentVersion,
                          Map<String, String> properties, List<String> modules) {}

    public static PomInfo read(Path pom) {
        try {
            DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
            f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            f.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            f.setXIncludeAware(false);
            f.setExpandEntityReferences(false);
            Document doc = f.newDocumentBuilder().parse(pom.toFile());
            Element project = doc.getDocumentElement();

            Element parent = child(project, "parent");
            String groupId = text(project, "groupId");
            if (groupId == null && parent != null) groupId = text(parent, "groupId");
            String version = text(project, "version");
            if (version == null && parent != null) version = text(parent, "version");

            Map<String, String> props = new HashMap<>();
            Element properties = child(project, "properties");
            if (properties != null) {
                for (Node n = properties.getFirstChild(); n != null; n = n.getNextSibling()) {
                    if (n instanceof Element e) props.put(e.getTagName(), e.getTextContent().trim());
                }
            }
            List<String> modules = new ArrayList<>();
            Element mods = child(project, "modules");
            if (mods != null) {
                for (Node n = mods.getFirstChild(); n != null; n = n.getNextSibling()) {
                    if (n instanceof Element e && e.getTagName().equals("module")) modules.add(e.getTextContent().trim());
                }
            }
            return new PomInfo(groupId, text(project, "artifactId"), version,
                    parent == null ? null : text(parent, "artifactId"),
                    parent == null ? null : text(parent, "version"), props, modules);
        } catch (Exception e) {
            throw new IllegalStateException("Cannot read " + pom + ": " + e.getMessage(), e);
        }
    }

    private static Element child(Element parent, String name) {
        for (Node n = parent.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n instanceof Element e && e.getTagName().equals(name)) return e;
        }
        return null;
    }

    private static String text(Element parent, String name) {
        Element e = child(parent, name);
        return e == null ? null : e.getTextContent().trim();
    }
}
