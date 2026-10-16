package gameplatform.member.util;

public class NationalIdValidator {

    public static boolean isValid(String nationalId) {
        // 1. 檢查是否為空，以及是否符合基本格式 (1個大寫字母 + 1或2 + 8個數字)
        if (nationalId == null || !nationalId.matches("^[A-Z][12]\\d{8}$")) {
            return false;
        }

        // 2. 字母對應的代碼表 (依照內政部標準)
        String letters = "ABCDEFGHJKLMNPQRSTUVXYWZIO";
        int letterIndex = letters.indexOf(nationalId.charAt(0));
        if (letterIndex == -1)
            return false;

        int d0 = letterIndex + 10;
        int n1 = d0 / 10;
        int n2 = d0 % 10;

        // 3. 定義加權權重
        int[] weights = { 1, 9, 8, 7, 6, 5, 4, 3, 2, 1, 1 };

        // 4. 將所有數字放進陣列
        int[] digits = new int[11];
        digits[0] = n1;
        digits[1] = n2;
        for (int i = 0; i < 9; i++) {
            digits[i + 2] = nationalId.charAt(i + 1) - '0';
        }

        // 5. 加權總和計算
        int sum = 0;
        for (int i = 0; i < 11; i++) {
            sum += digits[i] * weights[i];
        }

        // 6. 總和必須能被 10 整除
        return sum % 10 == 0;
    }
}
