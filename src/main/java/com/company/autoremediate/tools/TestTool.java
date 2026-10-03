package com.company.autoremediate.tools;

import com.company.autoremediate.model.TestSummary;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/** Deterministic test result parsing from Surefire/Failsafe XML reports (all modules). */
@Component
public class TestTool {

    public TestSummary summarize(Path repo) {
        int run = 0, failures = 0, errors = 0, skipped = 0;
        List<String> failed = new ArrayList<>();
        for (Path report : findReports(repo)) {
            try {
                Document doc = parse(report);
                Element root = doc.getDocumentElement();
                if (!"testsuite".equals(root.getTagName())) continue;
                run += intAttr(root, "tests");
                failures += intAttr(root, "failures");
                errors += intAttr(root, "errors");
                skipped += intAttr(root, "skipped");
                NodeList cases = root.getElementsByTagName("testcase");
                for (int i = 0; i < cases.getLength(); i++) {
                    Element tc = (Element) cases.item(i);
                    boolean bad = tc.getElementsByTagName("failure").getLength() > 0
                            || tc.getElementsByTagName("error").getLength() > 0;
                    if (bad) failed.add(tc.getAttribute("classname") + "." + tc.getAttribute("name"));
                }
            } catch (Exception e) {
                // an unreadable report must not look like a pass
                errors += 1;
                failed.add("UNREADABLE_REPORT:" + report.getFileName());
            }
        }
        return new TestSummary(run, failures, errors, skipped, failed);
    }

    private List<Path> findReports(Path repo) {
        try (Stream<Path> s = Files.walk(repo)) {
            return s.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().startsWith("TEST-")
                            && p.getFileName().toString().endsWith(".xml"))
                    .filter(p -> {
                        String parent = p.getParent().getFileName().toString();
                        return parent.equals("surefire-reports") || parent.equals("failsafe-reports");
                    })
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static int intAttr(Element e, String name) {
        String v = e.getAttribute(name);
        return v == null || v.isBlank() ? 0 : Integer.parseInt(v.trim());
    }

    private static Document parse(Path file) throws Exception {
        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
        f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        f.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        f.setXIncludeAware(false);
        f.setExpandEntityReferences(false);
        DocumentBuilder b = f.newDocumentBuilder();
        return b.parse(file.toFile());
    }
}
