package com.openmanus.agentteam.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("PermissionEvaluator")
class PermissionEvaluatorTest {

    private PermissionEvaluator evaluator;

    @BeforeEach
    void setUp() {
        List<PermissionRule> rules = List.of(
                new PermissionRule("bash", "git status*", PermissionBehavior.ALLOW, 1, "允许 git status"),
                new PermissionRule("bash", "git diff*", PermissionBehavior.ALLOW, 1, "允许 git diff"),
                new PermissionRule("bash", "git log*", PermissionBehavior.ALLOW, 1, "允许 git log"),
                new PermissionRule("bash", "javac*", PermissionBehavior.ALLOW, 2, "允许 Java 编译"),
                new PermissionRule("bash", "mvn*", PermissionBehavior.ALLOW, 2, "允许 Maven"),
                new PermissionRule("bash", "npm*", PermissionBehavior.ALLOW, 2, "允许 npm"),
                new PermissionRule("bash", "python*", PermissionBehavior.ALLOW, 2, "允许 Python"),
                new PermissionRule("bash", "ls*", PermissionBehavior.ALLOW, 3, "允许 ls"),
                new PermissionRule("bash", "cat*", PermissionBehavior.ALLOW, 3, "允许 cat"),
                new PermissionRule("bash", "mkdir*", PermissionBehavior.ALLOW, 3, "允许 mkdir"),
                new PermissionRule("bash", "find*", PermissionBehavior.ALLOW, 3, "允许 find"),
                new PermissionRule("bash", "grep*", PermissionBehavior.ALLOW, 3, "允许 grep"),
                new PermissionRule("bash", "echo*", PermissionBehavior.ALLOW, 3, "允许 echo"),
                new PermissionRule("bash", "rm -rf*", PermissionBehavior.DENY, 10, "禁止递归强制删除"),
                new PermissionRule("bash", "curl*", PermissionBehavior.DENY, 10, "禁止 curl"),
                new PermissionRule("file", "src/**", PermissionBehavior.ALLOW, 5, "允许编辑 src/")
        );
        evaluator = new PermissionEvaluator(rules, PermissionBehavior.DENY);
    }

    @Nested
    @DisplayName("Allow rules")
    class AllowRules {

        @Test
        @DisplayName("allows git status (prefix match)")
        void allowsGitStatus() {
            PermissionCheckResult result = evaluator.evaluate("bash", "git status");
            assertTrue(result.allowed());
            assertEquals(PermissionBehavior.ALLOW, result.behavior());
        }

        @Test
        @DisplayName("allows git status --porcelain (prefix match with args)")
        void allowsGitStatusWithArgs() {
            PermissionCheckResult result = evaluator.evaluate("bash", "git status --porcelain");
            assertTrue(result.allowed());
        }

        @Test
        @DisplayName("allows javac compilation")
        void allowsJavac() {
            PermissionCheckResult result = evaluator.evaluate("bash", "javac Main.java");
            assertTrue(result.allowed());
        }

        @Test
        @DisplayName("allows mvn clean compile")
        void allowsMvn() {
            PermissionCheckResult result = evaluator.evaluate("bash", "mvn clean compile");
            assertTrue(result.allowed());
        }

        @Test
        @DisplayName("allows npm install")
        void allowsNpm() {
            PermissionCheckResult result = evaluator.evaluate("bash", "npm install");
            assertTrue(result.allowed());
        }

        @Test
        @DisplayName("allows python script execution")
        void allowsPython() {
            PermissionCheckResult result = evaluator.evaluate("bash", "python script.py");
            assertTrue(result.allowed());
        }

        @Test
        @DisplayName("allows file edit in src/")
        void allowsFileEditInSrc() {
            PermissionCheckResult result = evaluator.evaluate("file", "src/main/java/Main.java");
            assertTrue(result.allowed());
        }
    }

    @Nested
    @DisplayName("Deny rules (high priority)")
    class DenyRules {

        @Test
        @DisplayName("denies rm -rf")
        void deniesRmRf() {
            PermissionCheckResult result = evaluator.evaluate("bash", "rm -rf /tmp/test");
            assertFalse(result.allowed());
            assertEquals(PermissionBehavior.DENY, result.behavior());
            assertTrue(result.reason().contains("递归强制删除"));
        }

        @Test
        @DisplayName("denies curl")
        void deniesCurl() {
            PermissionCheckResult result = evaluator.evaluate("bash", "curl http://example.com");
            assertFalse(result.allowed());
        }
    }

    @Nested
    @DisplayName("Default behavior (no matching rule)")
    class DefaultBehavior {

        @Test
        @DisplayName("denies unlisted command when default is DENY")
        void deniesUnlistedCommand() {
            PermissionCheckResult result = evaluator.evaluate("bash", "some_unknown_tool --flag");
            assertFalse(result.allowed());
        }

        @Test
        @DisplayName("allows unlisted command when default is ALLOW")
        void allowsUnlistedWhenDefaultAllow() {
            PermissionEvaluator allowByDefault = new PermissionEvaluator(List.of(), PermissionBehavior.ALLOW);
            PermissionCheckResult result = allowByDefault.evaluate("bash", "some_unknown_tool --flag");
            assertTrue(result.allowed());
        }
    }

    @Nested
    @DisplayName("Priority ordering")
    class PriorityOrdering {

        @Test
        @DisplayName("higher priority allow overrides lower priority deny")
        void allowOverridesDeny() {
            // git status matches ALLOW at priority 1 before it could match any DENY at priority 10
            PermissionCheckResult result = evaluator.evaluate("bash", "git status");
            assertTrue(result.allowed());
        }

        @Test
        @DisplayName("first match wins")
        void firstMatchWins() {
            List<PermissionRule> conflictingRules = List.of(
                    new PermissionRule("bash", "test*", PermissionBehavior.ALLOW, 1, "allow first"),
                    new PermissionRule("bash", "test*", PermissionBehavior.DENY, 2, "deny second")
            );
            PermissionEvaluator conflictEval = new PermissionEvaluator(conflictingRules, PermissionBehavior.DENY);
            PermissionCheckResult result = conflictEval.evaluate("bash", "test command");
            assertTrue(result.allowed(), "Priority 1 ALLOW should win over priority 2 DENY");
        }
    }

    @Nested
    @DisplayName("Validation")
    class Validation {

        @Test
        @DisplayName("denies blank tool name")
        void deniesBlankToolName() {
            PermissionCheckResult result = evaluator.evaluate("", "some command");
            assertFalse(result.allowed());
            assertTrue(result.reason().contains("blank"));
        }

        @Test
        @DisplayName("denies null tool name")
        void deniesNullToolName() {
            PermissionCheckResult result = evaluator.evaluate(null, "some command");
            assertFalse(result.allowed());
        }
    }
}
