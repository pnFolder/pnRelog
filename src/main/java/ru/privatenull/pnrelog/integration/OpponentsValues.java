package ru.privatenull.pnrelog.integration;

import org.pnfolder.pnlibrary.api.placeholders.annotation.Argument;
import org.pnfolder.pnlibrary.api.placeholders.annotation.Placeholder;
import org.pnfolder.pnlibrary.api.placeholders.annotation.Placeholders;
import org.pnfolder.pnlibrary.api.placeholders.annotation.Viewer;
import org.pnfolder.pnlibrary.api.placeholders.argument.ArgumentValue;
import org.pnfolder.pnlibrary.api.placeholders.info.ViewerInfo;
import org.pnfolder.pnlibrary.api.placeholders.reply.Reply;

/**
 * {@code %pnrelog_opponents_contains_<ник>%}: есть ли ник среди противников зрителя. Сам {@code %pnrelog_opponents%}
 * регистрируется отдельно. Вид {@code _formatted} лежит в {@link OpponentsFormat}: тот же путь, другой класс.
 */
@Placeholders("opponents")
public final class OpponentsValues {
    private final PnRelogPlaceholders values;

    public OpponentsValues(PnRelogPlaceholders values) {
        this.values = values;
    }

    @Placeholder("Есть ли ник среди противников")
    public Reply<String> contains(@Viewer ViewerInfo viewer, @Argument ArgumentValue<String> name) {
        boolean has = viewer.getPlayerId() != null && values.hasOpponent(viewer.getPlayerId(), name.required());
        return Reply.value(Boolean.toString(has));
    }
}
