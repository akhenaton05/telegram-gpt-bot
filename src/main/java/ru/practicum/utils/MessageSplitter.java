package ru.practicum.utils;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class MessageSplitter {

    // Разбивает текст на чанки для Telegram, учитывая блоки кода <pre><code>...</code></pre>.
    public List<String> splitMessageForTelegram(String text, int maxLength) {
        List<String> chunks = new ArrayList<>();
        if (text == null || text.isEmpty()) return chunks;

        int pos = 0;
        while (pos < text.length()) {
            int nextPre = text.indexOf("<pre><code>", pos);

            if (nextPre == -1 || nextPre >= pos + maxLength) {
                // В чанке нет кода, обычный текст
                int end = Math.min(pos + maxLength, text.length());
                chunks.add(text.substring(pos, end));
                pos = end;
            } else {
                // Есть блок кода
                if (nextPre > pos) {
                    // Добавляем текст перед кодом
                    chunks.add(text.substring(pos, nextPre));
                }

                int codeStart = nextPre + "<pre><code>".length();
                int codeEnd = text.indexOf("</code></pre>", codeStart);
                if (codeEnd == -1) codeEnd = text.length();

                String codeBlock = text.substring(codeStart, codeEnd);

                // Разбиваем длинный код на чанки, каждый с открытием и закрытием тегов
                int codePos = 0;
                int safeLength = maxLength - "<pre><code></code></pre>".length();
                while (codePos < codeBlock.length()) {
                    int len = Math.min(safeLength, codeBlock.length() - codePos);
                    String chunk = "<pre><code>" +
                            codeBlock.substring(codePos, codePos + len) +
                            "</code></pre>";
                    chunks.add(chunk);
                    codePos += len;
                }

                pos = codeEnd + "</code></pre>".length();
            }
        }

        return chunks;
    }
}
