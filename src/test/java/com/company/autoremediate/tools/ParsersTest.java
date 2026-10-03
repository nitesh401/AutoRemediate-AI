package com.company.autoremediate.tools;

import static org.assertj.core.api.Assertions.assertThat;

import com.company.autoremediate.model.SnykIssue;
import com.company.autoremediate.model.SonarIssue;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

class ParsersTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void parsesSonarIssuesUsingComponentPaths() throws Exception {
        String json = """
                {"paging":{"total":2},
                 "issues":[
                  {"key":"AX1","rule":"java:S1128","severity":"MINOR","type":"CODE_SMELL",
                   "component":"g:a:src/main/java/A.java","line":3,"message":"Remove this unnecessary import"},
                  {"key":"AX2","rule":"java:S1481","severity":"MINOR","type":"CODE_SMELL",
                   "component":"g:a:src/main/java/B.java","message":"Remove unused local variable"}],
                 "components":[{"key":"g:a:src/main/java/A.java","path":"src/main/java/A.java"}]}
                """;
        List<SonarIssue> issues = SonarIssueParser.parse(mapper.readTree(json));
        assertThat(issues).hasSize(2);
        assertThat(issues.get(0).file()).isEqualTo("src/main/java/A.java");
        assertThat(issues.get(0).line()).isEqualTo(3);
        assertThat(issues.get(1).file()).isEqualTo("src/main/java/B.java"); // fallback: after last ':'
        assertThat(issues.get(1).line()).isNull();
    }

    @Test
    void parsesSnykVulnerabilities() throws Exception {
        String json = """
                {"vulnerabilities":[
                 {"id":"SNYK-JAVA-1","packageName":"org.apache.commons:commons-text","version":"1.9",
                  "severity":"critical","title":"Arbitrary Code Execution",
                  "from":["demo@1.0","org.apache.commons:commons-text@1.9"],"fixedIn":["1.10.0"]},
                 {"id":"SNYK-JAVA-2","packageName":"x:y","version":"1.0","severity":"high","title":"t",
                  "from":["demo@1.0","a:b@1","x:y@1.0"],"fixedIn":["1.1","2.0"]},
                 {"id":"SNYK-JAVA-1","packageName":"org.apache.commons:commons-text","version":"1.9",
                  "severity":"critical","title":"duplicate path",
                  "from":["demo@1.0","other@1","org.apache.commons:commons-text@1.9"],"fixedIn":["1.10.0"]}]}
                """;
        List<SnykIssue> issues = SnykJsonParser.parse(mapper.readTree(json));
        assertThat(issues).hasSize(2);
        assertThat(issues.get(0).directDependency()).isTrue();
        assertThat(issues.get(0).recommendedVersion()).isEqualTo("1.10.0");
        assertThat(issues.get(1).directDependency()).isFalse();
        assertThat(issues.get(1).recommendedVersion()).isEqualTo("1.1");
    }

    @Test
    void parsesProjectArrays() throws Exception {
        String json = "[{\"vulnerabilities\":[]},{\"vulnerabilities\":[{\"id\":\"S\",\"packageName\":\"a:b\","
                + "\"version\":\"1.0\",\"severity\":\"low\",\"title\":\"t\",\"from\":[\"p\",\"a:b@1.0\"],\"fixedIn\":[\"1.0.1\"]}]}]";
        assertThat(SnykJsonParser.parse(mapper.readTree(json))).hasSize(1);
    }
}
