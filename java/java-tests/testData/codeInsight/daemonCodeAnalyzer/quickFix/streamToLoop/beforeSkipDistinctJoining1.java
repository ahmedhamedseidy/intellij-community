// "Replace Stream API chain with loop" "true"

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class Main {
  private static String test(List<CharSequence> list, String delimiter) {
    return list.stream().skip(1).distinct().skip(2).c<caret>ollect(Collectors.joining(delimiter));
  }

  public static void main(String[] args) {
    System.out.println(test(Arrays.asList("a", "b", "e", "c", "d", "e", "a"), ";"));
  }
}