package com.resumetailor.service;

/**
 * Inline HTML templates — package-private so only NotificationService uses them.
 * Replace each method body with a Thymeleaf/FreeMarker call when you outgrow strings.
 */
final class EmailTemplates {

    private EmailTemplates() {}

    static String welcome(String name) {
        return """
                <html>
                <body style="font-family:Arial,sans-serif;max-width:600px;margin:0 auto;color:#222">
                  <h2 style="color:#4F46E5">Welcome to Resume Tailor, %s!</h2>
                  <p>Your account is ready. Start tailoring your resume with AI — no extra setup required.</p>
                  <p style="margin-top:32px">If anything feels off, just reply to this email.</p>
                  %s
                </body>
                </html>
                """.formatted(name, footer());
    }

    static String passwordReset(String name, String resetLink) {
        return """
                <html>
                <body style="font-family:Arial,sans-serif;max-width:600px;margin:0 auto;color:#222">
                  <h2>Password Reset Request</h2>
                  <p>Hi %s,</p>
                  <p>Click the button below to reset your password.
                     <strong>This link expires in 1 hour.</strong></p>
                  <p style="margin:24px 0">
                    <a href="%s"
                       style="padding:12px 28px;background:#4F46E5;color:#fff;
                              text-decoration:none;border-radius:6px;font-weight:bold">
                      Reset Password
                    </a>
                  </p>
                  <p style="color:#888;font-size:13px">
                    Or copy this link into your browser:<br>
                    <a href="%s" style="color:#4F46E5">%s</a>
                  </p>
                  <p>If you didn't request this, you can safely ignore this email.</p>
                  %s
                </body>
                </html>
                """.formatted(name, resetLink, resetLink, resetLink, footer());
    }

    static String purchaseConfirmation(String name, int creditsPurchased, int newBalance) {
        return """
                <html>
                <body style="font-family:Arial,sans-serif;max-width:600px;margin:0 auto;color:#222">
                  <h2 style="color:#16A34A">Purchase Confirmed!</h2>
                  <p>Hi %s,</p>
                  <p>Your payment was processed successfully. Here is your summary:</p>
                  <table style="border-collapse:collapse;width:100%%;margin:16px 0">
                    <tr style="background:#F3F4F6">
                      <td style="padding:10px 14px;border:1px solid #E5E7EB">Credits purchased</td>
                      <td style="padding:10px 14px;border:1px solid #E5E7EB;font-weight:bold">%d</td>
                    </tr>
                    <tr>
                      <td style="padding:10px 14px;border:1px solid #E5E7EB">New balance</td>
                      <td style="padding:10px 14px;border:1px solid #E5E7EB;font-weight:bold">%d credits</td>
                    </tr>
                  </table>
                  <p>Head back to the app to start tailoring your resume.</p>
                  %s
                </body>
                </html>
                """.formatted(name, creditsPurchased, newBalance, footer());
    }

    // ── shared footer ─────────────────────────────────────────────────────────

    private static String footer() {
        return """
                <hr style="border:none;border-top:1px solid #E5E7EB;margin-top:40px">
                <p style="color:#9CA3AF;font-size:12px">
                  Resume Tailor · AI-powered resume optimization<br>
                  You are receiving this email because you have an account with us.
                </p>
                """;
    }
}
