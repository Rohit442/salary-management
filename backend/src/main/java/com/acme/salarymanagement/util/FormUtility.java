package com.acme.salarymanagement.util;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class FormUtility {
    private FormUtility() { }

    public static Map<String, String[]> decode(byte[] body) {
        Map<String, List<String>> values = new LinkedHashMap<>();
        for (String pair : new String(body, StandardCharsets.ISO_8859_1).split("&", -1)) {
            if (pair.isEmpty()) continue;
            int separator = pair.indexOf('=');
            String name = decodePart(separator < 0 ? pair : pair.substring(0, separator));
            String value = decodePart(separator < 0 ? "" : pair.substring(separator + 1));
            values.computeIfAbsent(name, ignored -> new ArrayList<>()).add(value);
        }
        Map<String, String[]> result = new LinkedHashMap<>();
        values.forEach((name, entries) -> result.put(name, entries.toArray(String[]::new)));
        return result;
    }

    private static String decodePart(String input) {
        var bytes = new ByteArrayOutputStream();
        for (int index = 0; index < input.length(); index++) {
            char character = input.charAt(index);
            if (character == '%') {
                if (index + 2 >= input.length()) throw new IllegalArgumentException("Malformed form encoding");
                int high = Character.digit(input.charAt(++index), 16);
                int low = Character.digit(input.charAt(++index), 16);
                if (high < 0 || low < 0) throw new IllegalArgumentException("Malformed form encoding");
                bytes.write(high * 16 + low);
            } else {
                bytes.write(character == '+' ? ' ' : character);
            }
        }
        try {
            return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes.toByteArray())).toString();
        } catch (CharacterCodingException exception) {
            throw new IllegalArgumentException("Malformed UTF-8 form encoding");
        }
    }
}
