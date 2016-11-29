// "Replace Stream API chain with loop" "true"

import java.util.Arrays;
import java.util.List;
import java.util.LongSummaryStatistics;

public class Main {
  public static LongSummaryStatistics test(List<List<String>> list) {
    return list.stream().filter(a -> a != null).flatMapToLong(lst -> lst.stream().distinct().mapToLong(a -> a.length())).summa<caret>ryStatistics();
  }

  public static void main(String[] args) {
    System.out.println(test(Arrays.asList(null, Arrays.asList("aaa", "b", "cc", "cc", "dddd"), Arrays.asList("cc", "b", "gggg"))));
  }
}