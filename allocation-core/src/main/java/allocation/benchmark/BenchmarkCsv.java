package allocation.benchmark;

final class BenchmarkCsv {

    private BenchmarkCsv() {
    }

    static String text(String value) {
        if (value == null) {
            return "";
        }

        String protectedValue = protectFromFormula(value);
        boolean requiresQuotes = protectedValue.contains(",")
                || protectedValue.contains("\"")
                || protectedValue.contains("\n")
                || protectedValue.contains("\r");

        if (!requiresQuotes) {
            return protectedValue;
        }

        return "\"" + protectedValue.replace("\"", "\"\"") + "\"";
    }

    static String number(Number value) {
        return value == null ? "" : value.toString();
    }

    static String bool(boolean value) {
        return Boolean.toString(value);
    }

    private static String protectFromFormula(String value) {
        int index = 0;

        while (index < value.length() && Character.isWhitespace(value.charAt(index))) {
            index++;
        }

        if (index < value.length() && isFormulaPrefix(value.charAt(index))) {
            return "'" + value;
        }

        return value;
    }

    private static boolean isFormulaPrefix(char value) {
        return value == '=' || value == '+' || value == '-' || value == '@' || value == '*';
    }
}
