package com.example;

import picocli.CommandLine;

import java.io.PrintWriter;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;

public class MyParameterExceptionHandler implements CommandLine.IParameterExceptionHandler {
  @Override
  public int handleParseException(CommandLine.ParameterException ex, String[] args) {
    CommandLine cmd = ex.getCommandLine();
    internalHandleParseException(ex, cmd.getErr(), cmd.getColorScheme());
    return mappedExitCode(ex, cmd.getExitCodeExceptionMapper(), cmd.getCommandSpec().exitCodeOnInvalidInput());
  }

  static void internalHandleParseException(CommandLine.ParameterException ex, PrintWriter writer, CommandLine.Help.ColorScheme colorScheme) {
    writer.println(colorScheme.errorText(ex.getMessage()));
    if (!printSuggestions(ex, writer)) {
      ex.getCommandLine().usage(writer, colorScheme);
    }
  }

  static boolean printSuggestions(CommandLine.ParameterException ex, PrintWriter writer) {
    return ex instanceof CommandLine.UnmatchedArgumentException uae && printSuggestions(uae, writer);
  }

  static boolean printSuggestions(CommandLine.UnmatchedArgumentException uae, PrintWriter writer) {
    List<String> suggestions = getSuggestions(uae);
    if (!suggestions.isEmpty()) {
      writer.println(uae.isUnknownOption()
          ? "Possible solutions: " + str(suggestions)
          : "Did you mean: " + str(suggestions).replace(", ", " or ") + "?");
      writer.flush();
    }
    return !suggestions.isEmpty();
  }

  static List<String> getSuggestions(CommandLine.UnmatchedArgumentException uae) {
    if (uae.getUnmatched().isEmpty()) { return Collections.emptyList(); }
    String arg = uae.getUnmatched().get(0);
    String stripped = stripPrefix(arg);
    CommandLine.Model.CommandSpec spec = uae.getCommandLine().getCommandSpec();
    if (resemblesOption(spec, arg)) {
      List<String> visibleOptions = spec.options().stream()
          .filter(option -> !option.hidden())
          .flatMap(option -> Arrays.stream(option.names()))
          .toList();

      List<String> mostSimilar = CosineSimilarity.mostSimilar(stripped, visibleOptions);
      return mostSimilar.subList(0, Math.min(3, mostSimilar.size()));
    } else if (!spec.subcommands().isEmpty()) {
      List<String> visibleSubs = new ArrayList<String>();
      for (Map.Entry<String, CommandLine> entry : spec.subcommands().entrySet()) {
        if (!entry.getValue().getCommandSpec().usageMessage().hidden()) { visibleSubs.add(entry.getKey()); }
      }
      List<String> mostSimilar = CosineSimilarity.mostSimilar(arg, visibleSubs);
      return mostSimilar.subList(0, Math.min(3, mostSimilar.size()));
    }
    return Collections.emptyList();
  }

  static String stripPrefix(String prefixed) {
    for (int i = 0; i < prefixed.length(); i++) {
      if (Character.isJavaIdentifierPart(prefixed.charAt(i))) { return prefixed.substring(i); }
    }
    return prefixed;
  }

  static boolean resemblesOption(CommandLine.Model.CommandSpec spec, String arg) {
    if (arg == null) { return false; }
    if (arg.length() == 1) {
      return false;
    }
    try {
      Long.decode(arg);
      return false;
    } catch (NumberFormatException nan) {} // negative numbers are not unknown options
    try {
      Double.parseDouble(arg);
      return false;
    } catch (NumberFormatException nan) {} // negative numbers are not unknown options

    if (spec.options().isEmpty()) {
      boolean result = arg.startsWith("-");
      return result;
    }
    int count = 0;
    for (String optionName : spec.optionsMap().keySet()) {
      for (int i = 0; i < arg.length(); i++) {
        if (optionName.length() > i && arg.charAt(i) == optionName.charAt(i)) { count++; } else { break; }
      }
    }
    boolean result = count > 0 && count * 10 >= spec.optionsMap().size() * 9; // at least one prefix char in common with 9 out of 10 options
    return result;
  }

  static String str(List<String> list) {
    String s = list.toString();
    return s.substring(0, s.length() - 1).substring(1);
  }

  static int mappedExitCode(Throwable t, CommandLine.IExitCodeExceptionMapper mapper, int defaultExitCode) {
    try {
      return (mapper != null) ? mapper.getExitCode(t) : defaultExitCode;
    } catch (Exception ex) {
      ex.printStackTrace();
      return defaultExitCode;
    }
  }

  private static class CosineSimilarity {
    static List<String> mostSimilar(String pattern, Iterable<String> candidates) { return mostSimilar(pattern, candidates, 0); }

    static List<String> mostSimilar(String pattern, Iterable<String> candidates, double threshold) {
      pattern = pattern.toLowerCase();
      SortedMap<Double, String> sorted = new TreeMap<Double, String>();
      for (String candidate : candidates) {
        double score = similarity(pattern, candidate.toLowerCase(), 2);
        if (score > threshold) { sorted.put(score, candidate); }
      }
      return reverseList(new ArrayList<String>(sorted.values()));
    }

    private static double similarity(String sequence1, String sequence2, int degree) {
      Map<String, Integer> m1 = countNgramFrequency(sequence1, degree);
      Map<String, Integer> m2 = countNgramFrequency(sequence2, degree);
      return dotProduct(m1, m2) / Math.sqrt(dotProduct(m1, m1) * dotProduct(m2, m2));
    }

    private static Map<String, Integer> countNgramFrequency(String sequence, int degree) {
      Map<String, Integer> m = new HashMap<String, Integer>();
      for (int i = 0; i + degree <= sequence.length(); i++) {
        String gram = sequence.substring(i, i + degree);
        m.put(gram, 1 + (m.containsKey(gram) ? m.get(gram) : 0));
      }
      return m;
    }

    private static double dotProduct(Map<String, Integer> m1, Map<String, Integer> m2) {
      double result = 0;
      for (String key : m1.keySet()) { result += m1.get(key) * (m2.containsKey(key) ? m2.get(key) : 0); }
      return result;
    }

    private static <T> List<T> reverseList(List<T> list) {
      Collections.reverse(list);
      return list;
    }
  }
}
