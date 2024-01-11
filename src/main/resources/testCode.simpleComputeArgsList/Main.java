import java.util.HashMap;
import java.util.Map;

public class Main {
    public static void main(String[] args) {
        if (args.length < 3) {
            System.out.println("参数至少三位");
            return;
        }
        int[] nums = new int[args.length - 1];
        for (int i = 0; i < args.length - 1; i++) {
            nums[i] = Integer.parseInt(args[i]);
        }
        int target = Integer.parseInt(args[args.length - 1]);

        Map<Integer, Integer> map = new HashMap<>();

        for (int i = 0; i < nums.length; i++) {
            int complement = target - nums[i];
            if (map.containsKey(complement)) {
                System.out.println(map.get(complement) + "," + i);
                return;
            }
            map.put(nums[i], i);
        }
        System.out.println("未找到");
    }
}