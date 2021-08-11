package com.example;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;
import picocli.CommandLine.*;

import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Path;
import java.util.concurrent.Callable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SuggestionTest {

  @Test
  @DisplayName("サジェスチョンがあれば Possible Solutions を表示する")
  void whenSuggestionsExist() {
    @Command(name = "for-test", mixinStandardHelpOptions = true)
    class MyCommand implements Callable<Integer> {

      @Option(names = {"--foo"})
      private String option1;

      @Option(names = {"--bar"})
      private String option2;

      @Override
      public Integer call() {
        return 0;
      }
    }

    var cmd = new CommandLine(new MyCommand());
    var sw = new StringWriter();
    cmd.setOut(new PrintWriter(sw));
    cmd.setErr(new PrintWriter(sw));

    cmd.execute("--foa", "abcde");

    assertEquals("""
        Unknown options: '--foa', 'abcde'
        Possible solutions: --foo
        """, sw.toString());
  }

  @Test
  @DisplayName("サジェスチョンがなければ Usage を表示する")
  void whenNoSuggestionExists() {
    @Command(name = "for-test", mixinStandardHelpOptions = true)
    class MyCommand implements Callable<Integer> {

      @Option(names = {"--foo"})
      private String option1;

      @Option(names = {"--bar"})
      private String option2;

      @Override
      public Integer call() {
        return 0;
      }
    }

    var cmd = new CommandLine(new MyCommand());
    var sw = new StringWriter();
    cmd.setOut(new PrintWriter(sw));
    cmd.setErr(new PrintWriter(sw));

    cmd.execute("--bufferings", "abcde");

    assertEquals("""
        Unknown options: '--bufferings', 'abcde'
        Usage: for-test [-hV] [--bar=<option2>] [--foo=<option1>]
              --bar=<option2>
              --foo=<option1>
          -h, --help            Show this help message and exit.
          -V, --version         Print version information and exit.
        """, sw.toString());
  }

  @Test
  @DisplayName("サジェスチョンは最初の2文字をチェックしてる")
  void getSuggestions_ShouldCheckFirst2Characters() {
    @Command(name = "for-test", mixinStandardHelpOptions = true)
    class MyCommand implements Callable<Integer> {

      @Option(names = {"--ab123"})
      private String option1;

      @Option(names = {"--ab234"})
      private String option2;

      @Option(names = {"--ac123"})
      private String option3;

      @Override
      public Integer call() {
        return 0;
      }
    }

    var cmd = new CommandLine(new MyCommand());

    var ex1 = assertThrows(UnmatchedArgumentException.class,
        () -> cmd.parseArgs("--foo", "abcde"));
    assertEquals("[]", ex1.getSuggestions().toString());

    var ex2 = assertThrows(UnmatchedArgumentException.class,
        () -> cmd.parseArgs("--abx", "abcde"));
    assertEquals("[--ab123, --ab234]", ex2.getSuggestions().toString());

    var ex3 = assertThrows(UnmatchedArgumentException.class,
        () -> cmd.parseArgs("--b1", "abcde"));
    assertEquals("[]", ex3.getSuggestions().toString());

    var ex4 = assertThrows(UnmatchedArgumentException.class,
        () -> cmd.parseArgs("--ac", "abcde"));
    assertEquals("[--ac123]", ex4.getSuggestions().toString());
  }

  @Test
  @DisplayName("サブコマンドの場合は最初の2文字じゃなくて似たものをサジェスチョンで出してくれる")
  void whenSubcommand() {
    @Command(name = "mygit", mixinStandardHelpOptions = true, subcommands = {HelpCommand.class})
    class MyGit {
      @Option(names = "--git-dir")
      Path path;

      @Command
      void commit(@Option(names = {"-m", "--message"}) String commitMessage,
                  @Option(names = "--squash", paramLabel = "<commit>") String squash,
                  @Parameters(paramLabel = "<file>") File[] files) {
      }

      @Command
      void squash(@Option(names = {"-m", "--message"}) String commitMessage,
                  @Option(names = "--squash", paramLabel = "<commit>") String squash,
                  @Parameters(paramLabel = "<file>") File[] files) {
      }
    }

    var cmd = new CommandLine(new MyGit());
    var sw = new StringWriter();
    cmd.setOut(new PrintWriter(sw));
    cmd.setErr(new PrintWriter(sw));

    cmd.execute("mmit", "--bufferings", "abcde");

    assertEquals("""
        Unmatched arguments from index 0: 'mmit', '--bufferings', 'abcde'
        Did you mean: commit?
        """, sw.toString());
  }

  @Test
  @DisplayName("オプションの場合でも最初の2文字じゃなくて似たものをサジェスチョンで出すように実装")
  void optionSuggestionModified() {
    @Command(name = "for-test", mixinStandardHelpOptions = true)
    class MyCommand implements Callable<Integer> {

      @Option(names = {"--bufferings"})
      private String option1;

      @Option(names = {"--algorithm"})
      private String option2;

      @Override
      public Integer call() {
        return 0;
      }
    }

    var cmd = new CommandLine(new MyCommand())
        .setParameterExceptionHandler(new MyParameterExceptionHandler());
    var sw = new StringWriter();
    cmd.setOut(new PrintWriter(sw));
    cmd.setErr(new PrintWriter(sw));

    cmd.execute("--uff", "abcde");

    assertEquals("""
        Unknown options: '--uff', 'abcde'
        Possible solutions: --bufferings
        """, sw.toString());
  }

}