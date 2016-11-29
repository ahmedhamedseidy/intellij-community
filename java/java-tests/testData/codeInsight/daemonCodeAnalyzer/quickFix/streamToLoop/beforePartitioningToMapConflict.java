// "Replace Stream API chain with loop" "true"

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

public class Main {
  public static void test(List<String> strings) {
    System.out.println(strings.stream().map(x -> x.trim()).colle<caret>ct(Collectors.partitioningBy(s -> s.length() > 2,
                                                                                             Collectors.toMap(s -> ((UnaryOperator<String>) x -> x).apply(s), String::length))));
  }

  public static void main(String[] args) {
    test(Arrays.asList("a", "bbb", "cccc", "dddd", "ee", "e", "e"));
  }
}
