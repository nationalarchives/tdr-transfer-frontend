#!/usr/bin/python3
import subprocess
import sys
import os
from collections import defaultdict


def get_list_of_staged_files():
    staged_files = subprocess.run("git diff --name-only --cached", shell=True, capture_output=True,
                                  text=True).stdout
    return staged_files.rstrip("\n").split("\n")


def group_files_by_extension(list_of_staged_files):
    staged_files_grouped_by_extension = defaultdict(list)

    for file in list_of_staged_files:
        if "." in file:
            extension_name = file[file.find(".") + 1:]
            staged_files_grouped_by_extension[extension_name].append(file)

    return staged_files_grouped_by_extension


def check_which_scala_checks_should_be_run(scala_files):
    return {
        "sbt scalafmtCheck": any("app/" in scala_file for scala_file in scala_files),
        "sbt test:scalafmtCheck": any("test/" in scala_file for scala_file in scala_files)
    }


def run_scalafmt_commands(scalafmt_commands_to_run):
    for scalafmt_command, run_scalafmt_command in scalafmt_commands_to_run.items():
        if run_scalafmt_command:
            subprocess.run(["echo", f"""\nRunning "{scalafmt_command}"\n"""])
            try:
                subprocess.run(f"{scalafmt_command}", shell=True, check=True)
            except subprocess.CalledProcessError:
                sys.exit(f"\nA error occurred after running '{scalafmt_command}'. Please check the error above.\n")


def run_npx_lint_staged():
    subprocess.run(["echo", """\nRunning "npx lint-staged"\n"""])

    try:
        subprocess.run("cd npm && npx lint-staged", shell=True, check=True)
    except subprocess.CalledProcessError:
        sys.exit("\nA ESLint error occurred. Please check the error above.\n")


def run_style_checkers(staged_files_grouped_by_extension):
    for extension, files in staged_files_grouped_by_extension.items():
        if extension in ["scala", "sc"]:
            scalafmt_commands_to_run = check_which_scala_checks_should_be_run(files)
            run_scalafmt_commands(scalafmt_commands_to_run)
        elif extension in ["ts", "scss"]:
            run_npx_lint_staged()
        else:
            continue


def get_associated_scala_tests_for_files(scala_file_names):
    test_file_names = [file_name for (parent_dir, current_dir, file_names) in os.walk("./test") for file_name in
                       file_names]
    tests_to_run = []

    for scala_file_name in scala_file_names:
        index_of_file_name = scala_file_name.rfind("/") + 1
        scala_file_name_without_path = scala_file_name[index_of_file_name:]
        index_of_extension = scala_file_name_without_path.find(".")
        scala_file_name_without_path_and_ext = scala_file_name_without_path[:index_of_extension]

        if "test/" in scala_file_name:  # if file is a test, then it doesn't need to find test file
            tests_to_run.append(scala_file_name_without_path)
            continue

        for test_file_name in test_file_names:
            if scala_file_name_without_path_and_ext in test_file_name:
                tests_to_run.append(test_file_name)

    return tests_to_run


def get_associated_scala_controllers_for_files(scala_html_files):
    associated_scala_controllers = []

    for scala_html_file in scala_html_files:
        index_of_file_name = scala_html_file.rfind("/") + 1
        scala_html_file_without_path = scala_html_file[index_of_file_name:]
        scala_html_file_without_path_and_ext = scala_html_file_without_path.replace(".scala.html", "")
        calling_view_file_pattern = f""""\.{scala_html_file_without_path_and_ext}(\""""

        controller_files = subprocess.run(
            f"""grep -rl {calling_view_file_pattern} "./app" "--exclude-dir=./app/views\"""",
            shell=True, capture_output=True, text=True).stdout

        associated_scala_controllers.extend(controller_files.rstrip("\n").split("\n"))

    return associated_scala_controllers


def run_scala_tests(list_of_scala_tests_to_run):
    file_names_without_ext = ["*" + scala_test.replace(".scala", "") for scala_test in list_of_scala_tests_to_run]
    file_names_as_string = " ".join(file_names_without_ext)
    subprocess.run(["echo", f"""\nRunning sbt "testOnly {file_names_as_string}"\n"""])

    try:
        subprocess.run(f"""sbt "testOnly {file_names_as_string}\"""", shell=True, check=True)
    except subprocess.CalledProcessError:
        sys.exit("\nA error occurred when running the Scala tests. Please check the error above.\n")


def run_npm_tests(list_of_npm_tests_to_run, run_all_tests):
    if run_all_tests:
        subprocess.run(["echo", f"""\nRunning "npm lint-staged\n"""])

        try:
            subprocess.run(f"""npx --prefix npm lint-staged""", shell=True, check=True)
        except subprocess.CalledProcessError:
            sys.exit("\nA error occurred when running npm test. Please check the error above.\n")

    else:
        file_names_as_string = " ".join(list_of_npm_tests_to_run)
        subprocess.run(["echo", f"""\nRunning "npm test -- {file_names_as_string}\n"""])

        try:
            subprocess.run(f"""npm --prefix npm test -- {file_names_as_string}""", shell=True, check=True)
        except subprocess.CalledProcessError:
            sys.exit(
                f"\nA error occurred when running npm test -- {file_names_as_string}. Please check the error above.\n")


def run_tests(staged_files_grouped_by_extension):
    list_of_scala_tests_to_run = set()

    run_all_typescript_tests = False
    list_typescript_tests_to_run = set()

    for extension, files in staged_files_grouped_by_extension.items():
        if extension in ["scala", "sc", "scala.html"]:
            if extension != "scala.html":
                # find test controllers with the same name as the controllers
                list_of_scala_tests = get_associated_scala_tests_for_files(files)
                list_of_scala_tests_to_run.update(list_of_scala_tests)
            else:
                # find controllers that call these views
                associated_controller_files = get_associated_scala_controllers_for_files(files)
                list_of_scala_tests = get_associated_scala_tests_for_files(associated_controller_files)
                list_of_scala_tests_to_run.update(list_of_scala_tests)
        elif extension == "test.ts":
            list_typescript_tests_to_run.update(files)
        elif extension == "ts":
            run_all_typescript_tests = True
        else:
            continue

    if list_of_scala_tests_to_run:
        run_scala_tests(list_of_scala_tests_to_run)

    if list_typescript_tests_to_run or run_all_typescript_tests:
        run_npm_tests(list_typescript_tests_to_run, run_all_typescript_tests)


def main():
    list_of_staged_files = get_list_of_staged_files()

    if list_of_staged_files:
        staged_files_grouped_by_extension = group_files_by_extension(list_of_staged_files)
        if any(extension in staged_files_grouped_by_extension for extension in ["scala", "sc", "ts"]):
            run_formatting_checks_response = input(
                "Run formatting checks on your staged files? Enter y or enter any key to skip: ").lower()
            run_tests_response = input("Run tests on your staged files? Enter y or enter any key to skip: ").lower()
            run_style_checkers(staged_files_grouped_by_extension) if run_formatting_checks_response == "y" else ""
            run_tests(staged_files_grouped_by_extension) if run_tests_response == "y" else ""


if __name__ == "__main__":
    main()
