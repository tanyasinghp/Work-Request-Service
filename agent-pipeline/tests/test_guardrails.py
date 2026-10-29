"""Guardrails run before the gate; every rule here has a test that proves it rejects."""

from pathlib import Path

import pytest

from guardrails import (
    added_test_names,
    check_changed_paths,
    check_diff,
    count_test_methods,
    diff_size,
    parse_diff,
)

MAIN_FILE = "backend/src/main/java/com/tanya/workrequests/domain/ProblemCode.java"
TEST_FILE = "backend/src/test/java/com/tanya/workrequests/processing/StaleRequestTest.java"


def modify(path: str, removed: list[str], added: list[str]) -> str:
    body = "\n".join([*(f"-{line}" for line in removed), *(f"+{line}" for line in added)])
    return f"diff --git a/{path} b/{path}\n--- a/{path}\n+++ b/{path}\n@@ -1,3 +1,3 @@\n context\n{body}\n"


def new_file(path: str, lines: list[str]) -> str:
    body = "\n".join(f"+{line}" for line in lines)
    return (f"diff --git a/{path} b/{path}\nnew file mode 100644\n--- /dev/null\n+++ b/{path}\n"
            f"@@ -0,0 +1,{len(lines)} @@\n{body}\n")


def delete_file(path: str, lines: list[str]) -> str:
    body = "\n".join(f"-{line}" for line in lines)
    return (f"diff --git a/{path} b/{path}\ndeleted file mode 100644\n--- a/{path}\n+++ /dev/null\n"
            f"@@ -1,{len(lines)} +0,0 @@\n{body}\n")


class TestAllowedChanges:
    def test_accepts_a_change_to_main_source(self):
        assert check_diff(modify(MAIN_FILE, ["    DUPLICATE,"], ["    DUPLICATE,", "    STALE_REQUEST,"])).ok

    def test_accepts_a_new_test_file(self):
        diff = new_file(TEST_FILE, ["class StaleRequestTest {", "    @Test", "    void flagsOldRequests() {",
                                    "        assertThat(1).isEqualTo(1);", "    }", "}"])
        assert check_diff(diff).ok

    def test_accepts_more_assertions_in_an_existing_test(self):
        diff = modify(TEST_FILE, ["        assertThat(a).isTrue();"],
                      ["        assertThat(a).isTrue();", "        assertThat(b).isFalse();"])
        result = check_diff(diff)
        assert result.ok
        assert result.warnings  # an existing assertion line was touched, so a reviewer should look


class TestPathRules:
    @pytest.mark.parametrize(("path", "reason"), [
        ("backend/pom.xml", "build file pom.xml"),
        (".github/workflows/ci.yml", "CI configuration"),
        ("backend/mvnw", "Maven wrapper"),
        ("backend/.mvn/wrapper/maven-wrapper.properties", "Maven wrapper"),
        ("backend/Dockerfile", "container configuration"),
        ("docker-compose.yml", "container configuration"),
        ("web/src/App.tsx", "outside backend/src/"),
        ("agent-pipeline/guardrails.py", "outside backend/src/"),
        ("backend/src/../../etc/passwd", "escapes the repository"),
    ])
    def test_rejects_paths_outside_backend_src(self, path, reason):
        result = check_diff(modify(path, ["a"], ["b"]))
        assert not result.ok
        assert any(reason in violation for violation in result.violations)

    def test_rejects_a_stage_writing_outside_its_own_area(self):
        result = check_diff(modify(MAIN_FILE, ["a"], ["b"]), allowed_prefix="backend/src/test/")
        assert any("outside backend/src/test/" in violation for violation in result.violations)

    def test_checks_both_sides_of_a_rename(self):
        diff = (f"diff --git a/{MAIN_FILE} b/backend/pom.xml\nsimilarity index 90%\n"
                f"rename from {MAIN_FILE}\nrename to backend/pom.xml\n")
        assert any("pom.xml" in violation for violation in check_diff(diff).violations)

    def test_applies_the_same_rules_to_paths_git_reports_after_applying(self):
        assert check_changed_paths([MAIN_FILE, TEST_FILE]) == []
        assert check_changed_paths(["backend/pom.xml"])

    def test_rejects_binary_patches(self):
        diff = f"diff --git a/{MAIN_FILE} b/{MAIN_FILE}\nGIT binary patch\nliteral 0\n"
        assert any("binary" in violation for violation in check_diff(diff).violations)


class TestTestProtection:
    def test_rejects_deleting_a_test_file(self):
        result = check_diff(delete_file(TEST_FILE, ["    @Test", "    void x() {}"]))
        assert any("deletes a test file" in violation for violation in result.violations)

    def test_rejects_renaming_a_test_file(self):
        other = TEST_FILE.replace("StaleRequestTest", "Other")
        diff = f"diff --git a/{TEST_FILE} b/{other}\nrename from {TEST_FILE}\nrename to {other}\n"
        assert any("renames a test file" in violation for violation in check_diff(diff).violations)

    def test_rejects_removing_a_test_method(self):
        diff = modify(TEST_FILE, ["    @Test", "    void keepsEarliest() {", "        assertThat(x).isTrue();"], [])
        violations = check_diff(diff).violations
        assert any("removes 1 test method" in violation for violation in violations)
        assert any("removes 1 assertion" in violation for violation in violations)

    def test_rejects_removing_assertions_even_when_tests_stay(self):
        diff = modify(TEST_FILE, ["        assertThat(result.problems()).hasSize(7);"], ["        // gone"])
        assert any("removes 1 assertion" in violation for violation in check_diff(diff).violations)

    @pytest.mark.parametrize("line", ["    @Disabled(\"flaky\")", "    @Ignore", "        assumeTrue(false);"])
    def test_rejects_disabling_tests(self, line):
        diff = modify(TEST_FILE, ["    @Test"], ["    @Test", line])
        assert any("disables or skips" in violation for violation in check_diff(diff).violations)

    def test_counts_mockmvc_expectations_as_assertions(self):
        diff = modify(TEST_FILE, ["                .andExpect(status().isOk())"], [])
        assert any("removes 1 assertion" in violation for violation in check_diff(diff).violations)


class TestMalformedDiffs:
    def test_rejects_an_empty_diff(self):
        assert check_diff("  \n").violations == ["diff is empty"]

    def test_rejects_text_that_is_not_a_diff(self):
        result = check_diff("Here is my change: add STALE_REQUEST to ProblemCode.")
        assert not result.ok
        assert "no file changes found" in result.violations[0]

    def test_parses_plain_unified_diffs_without_git_headers(self):
        diff = (f"--- a/{MAIN_FILE}\n+++ b/{MAIN_FILE}\n@@ -1 +1 @@\n-x\n+y\n"
                f"--- a/{TEST_FILE}\n+++ b/{TEST_FILE}\n@@ -1 +1,2 @@\n x\n+y\n")
        patches = parse_diff(diff)
        assert [patch.new_path for patch in patches] == [MAIN_FILE, TEST_FILE]
        assert patches[0].removed_lines == ["x"]
        assert patches[1].removed_lines == []


class TestReporting:
    def test_names_the_tests_a_diff_adds(self):
        diff = new_file(TEST_FILE, [
            "class StaleRequestTest {", "    @Test", "    void flagsOldRequests() {", "    }",
            "    @ParameterizedTest", "    @ValueSource(ints = 1)", "    void keepsNew(int d) {", "    }",
            "    private void helper() {}", "}"])
        assert added_test_names(diff) == ["StaleRequestTest.flagsOldRequests", "StaleRequestTest.keepsNew"]

    def test_measures_diff_size_in_changed_lines(self):
        assert diff_size(modify(MAIN_FILE, ["a", "b"], ["c"])) == 3

    def test_counts_test_methods_in_a_tree(self, tmp_path: Path):
        (tmp_path / "pkg").mkdir()
        (tmp_path / "pkg/ATest.java").write_text("@Test void a(){}\n@ParameterizedTest void b(){}\n", encoding="utf-8")
        (tmp_path / "BTest.java").write_text("@Test void c(){}\n", encoding="utf-8")
        assert count_test_methods(tmp_path) == 3
