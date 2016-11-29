// "Replace with sum()" "true"
import java.util.Arrays;
import java.util.List;

public class Main {
  public boolean check(Integer x) {
    return x % 3 == 0;
  }

  public boolean check(int x) {
    return x % 2 == 0;
  }

  public int sum(List<Integer> list) {
      int sum = list.stream().mapToInt(x -> x).filter(this::check).sum();
      return sum;
  }

  public static void main(String[] args) {
    System.out.println(new Main().sum(Arrays.asList(1,2,3,4,5,6)));
  }
}