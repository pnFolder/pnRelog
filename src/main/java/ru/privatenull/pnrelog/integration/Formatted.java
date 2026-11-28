package ru.privatenull.pnrelog.integration;

import org.pnfolder.pnlibrary.api.placeholders.annotation.Placeholder;
import org.pnfolder.pnlibrary.api.placeholders.reply.Reply;

/** Ветка {@code formatted}: тот же ответ в виде из настроек ({@code placeholders.*} в config.yml). */
public final class Formatted {
    private final String text;

    Formatted(String text) {
        this.text = text;
    }

    @Placeholder("Вид из настроек")
    public Reply<String> formatted() {
        return Reply.value(text);
    }
}
