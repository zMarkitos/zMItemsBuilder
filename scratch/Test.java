public class Test {
    public static void main(String[] args) {
        String s = " {primary_color}🕮&#770000 {gradient:Encantamientos:}";
        System.out.println(s.replaceAll("(?i)&#[A-Fa-f0-9]{6}", ""));
    }
}
