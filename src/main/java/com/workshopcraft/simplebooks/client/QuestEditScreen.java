package com.workshopcraft.simplebooks.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.workshopcraft.simplebooks.SimpleBooks;
import com.workshopcraft.simplebooks.quest.QuestClientStore;
import com.workshopcraft.simplebooks.quest.QuestDefs;
import com.workshopcraft.simplebooks.quest.QuestEditPacket;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Quest property editor. Tasks and rewards stack in a scrolling column so a short window and a long list both fit. */
public class QuestEditScreen extends Screen {
    private final String questId;
    private final @Nullable Screen parent;
    private EditBox title, subtitle, text, icon;
    private String titleKey = "", subtitleKey = "", textKey = "";
    private final List<QuestEditForms.TaskRow> taskRows = new ArrayList<>();
    private final List<QuestEditForms.RewardRow> rewardRows = new ArrayList<>();
    private final List<AbstractWidget> body = new ArrayList<>();
    private final Map<AbstractWidget, Integer> baseY = new HashMap<>();
    private JsonObject draft;
    private String error = "";
    private final List<int[]> labels = new ArrayList<>();
    private final List<String> labelNames = new ArrayList<>();
    private int scroll, contentHeight, viewTop, viewBottom, left, right, fx, fy;
    private boolean showNewTask, showNewReward, barDrag;

    public QuestEditScreen(String questId, @Nullable Screen parent) {
        super(Component.literal("Edit " + QuestEdit.shortName(questId)));
        this.questId = questId;
        this.parent = parent;
        QuestDefs.Quest q = find();
        draft = q == null ? new JsonObject() : q.json().deepCopy();
    }

    @Override
    protected void init() {
        clearWidgets();
        labels.clear();
        labelNames.clear();
        body.clear();
        baseY.clear();
        taskRows.clear();
        rewardRows.clear();
        viewTop = 4;
        viewBottom = Math.max(viewTop + 40, height - 50);
        left = 8;
        right = Math.max(left + 120, width - 12);
        titleKey = str(draft, "title");
        subtitleKey = str(draft, "subtitle");
        textKey = str(draft, "text");
        int y = viewTop;
        int fieldW = Math.min(360, right - left);
        title = labeled("Title", left, y, fieldW, QuestEditForms.displayText(titleKey)); y += 32;
        subtitle = labeled("Subtitle", left, y, fieldW, QuestEditForms.displayText(subtitleKey)); y += 32;
        icon = labeled("Icon", left, y, fieldW, str(draft, "icon")); y += 32;
        text = labeled("Text", left, y, fieldW, QuestEditForms.displayText(textKey)); y += 36;

        mark("Tasks", left, y); y += 14;
        JsonArray tarr = arr(draft, "tasks");
        if (tarr.isEmpty()) tarr.add(new JsonObject());
        for (int i = 0; i < tarr.size(); i++) y = layoutTask(y, i, tarr.get(i).getAsJsonObject(), tarr.size());
        y = plus(y, "+ Task", b -> {
            commitRows();
            arr(draft, "tasks").add(new JsonObject());
            showNewTask = true;
            rebuild();
        });
        int tasksBottom = y;
        y += 8;
        mark("Rewards", left, y); y += 14;
        JsonArray rarr = arr(draft, "rewards");
        if (rarr.isEmpty()) rarr.add(new JsonObject());
        for (int i = 0; i < rarr.size(); i++) y = layoutReward(y, i, rarr.get(i).getAsJsonObject(), rarr.size());
        y = plus(y, "+ Reward", b -> {
            commitRows();
            arr(draft, "rewards").add(new JsonObject());
            showNewReward = true;
            rebuild();
        });
        contentHeight = y + 4;
        int viewH = viewBottom - viewTop;
        if (showNewTask) {
            showNewTask = false;
            scroll = tasksBottom - viewH;
        }
        if (showNewReward) {
            showNewReward = false;
            scroll = contentHeight - viewH;
        }
        layoutScroll();

        int by = height - 28;
        addRenderableWidget(Button.builder(Component.literal("Save"), b -> save()).bounds(width / 2 - 100, by, 90, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Cancel"), b -> minecraft.setScreen(parent)).bounds(width / 2 + 10, by, 90, 20).build());
    }

    private int layoutTask(int y, int index, JsonObject json, int count) {
        String type = json.has("type") && json.get("type").isJsonPrimitive() ? json.get("type").getAsString() : "item";
        boolean raw = type.contains(":");
        row(y);
        flowLabel("Task " + (index + 1));
        int idx = index;
        if (count > 1) button("X", 20, b -> removeTask(idx));
        button("Type: " + type, b -> {
            QuestEditForms.TaskRow r = taskRows.get(idx);
            r.cycleType();
            commitRows();
            rebuild();
        });
        button(raw ? "Raw JSON" : "Form", b -> {
            QuestEditForms.TaskRow r = taskRows.get(idx);
            r.useRaw = !r.useRaw;
            commitRows();
            rebuild();
        });
        int fieldY = fy + 32;
        QuestEditForms.TaskRow task = new QuestEditForms.TaskRow(font, left, fieldY, json);
        taskRows.add(task);
        addTaskLabels(left, fieldY, task);
        return addTaskFields(left, fieldY, task) + 8;
    }

    private int layoutReward(int y, int index, JsonObject json, int count) {
        String kind = QuestEditForms.RewardRow.kindOf(json);
        row(y);
        flowLabel("Reward " + (index + 1));
        int idx = index;
        if (count > 1) button("X", 20, b -> removeReward(idx));
        button("Kind: " + kind, b -> {
            QuestEditForms.RewardRow r = rewardRows.get(idx);
            r.cycleKind();
            commitRows();
            rebuild();
        });
        button(kind.equals("custom") ? "Raw JSON" : "Form", b -> {
            QuestEditForms.RewardRow r = rewardRows.get(idx);
            r.useRaw = !r.useRaw;
            commitRows();
            rebuild();
        });
        int fieldY = fy + 32;
        QuestEditForms.RewardRow reward = new QuestEditForms.RewardRow(font, left, fieldY, json);
        rewardRows.add(reward);
        int bottom = addRewardFields(left, fieldY, reward);
        addRewardLabels(left, fieldY, reward);
        return bottom + 8;
    }

    private int plus(int y, String label, Button.OnPress on) {
        row(y);
        button(label, on);
        return fy + 26;
    }

    private void removeTask(int index) {
        commitRows();
        JsonArray a = arr(draft, "tasks");
        if (a.size() <= 1 || index < 0 || index >= a.size()) return;
        a.remove(index);
        rebuild();
    }

    private void removeReward(int index) {
        commitRows();
        JsonArray a = arr(draft, "rewards");
        if (a.size() <= 1 || index < 0 || index >= a.size()) return;
        a.remove(index);
        rebuild();
    }

    private void row(int y) {
        fx = left;
        fy = y;
    }

    /** Next slot on this line, or the line below when it would pass the right edge. */
    private int[] put(int w, int h) {
        w = Math.min(w, Math.max(20, right - left));
        if (fx > left && fx + w > right) {
            fx = left;
            fy += h + 2;
        }
        int x = fx;
        fx += w + 4;
        return new int[]{x, fy};
    }

    private void flowLabel(String name) {
        int w = font.width(name) + 8;
        if (fx > left && fx + w > right) {
            fx = left;
            fy += 22;
        }
        mark(name, fx, fy + 5);
        fx += w;
    }

    private void button(String label, Button.OnPress on) {
        button(label, font.width(label) + 12, on);
    }

    private void button(String label, int w, Button.OnPress on) {
        w = Math.min(Math.max(20, w), Math.max(20, right - left));
        int[] p = put(w, 20);
        body(Button.builder(Component.literal(label), on).bounds(p[0], p[1], w, 20).build());
    }

    private EditBox labeled(String name, int x, int y, int w, String value) {
        mark(name, x, y);
        return addField(x, y + 10, w, value);
    }

    private void mark(String name, int x, int y) {
        labelNames.add(name);
        labels.add(new int[]{x, y});
    }

    private void addTaskLabels(int x, int y, QuestEditForms.TaskRow row) {
        if (row.useRaw) { mark("Raw JSON", x, y - 10); return; }
        mark("Id", x, y - 10);
        switch (row.type) {
            case "item", "craft", "smelt", "pickup", "use" -> {
                mark("Item", x + 90, y - 10);
                mark("Count", x + 240, y - 10);
            }
            case "place", "break" -> { mark("Block", x, y + 12); mark("Count", x + 150, y + 12); }
            case "kill" -> { mark("Entity", x, y + 12); mark("Count", x + 150, y + 12); }
            case "dimension" -> mark("Dimension", x, y + 12);
            case "biome" -> mark("Biome", x, y + 12);
            case "advancement" -> mark("Advancement", x, y + 12);
            case "stat" -> { mark("Stat", x, y + 12); mark("Count", x + 210, y + 12); }
            case "checkbox" -> mark("Checkbox text", x, y + 12);
            default -> mark("Raw JSON", x, y + 12);
        }
    }

    private void addRewardLabels(int x, int y, QuestEditForms.RewardRow row) {
        if (row.useRaw) { mark("Raw JSON", x, y - 10); return; }
        switch (row.kind) {
            case "item" -> { mark("Item", x, y - 10); mark("Count", x + 150, y - 10); }
            case "xp", "xp_levels" -> mark("Amount", x, y - 10);
            case "command" -> {
                mark("Command", x, y - 10);
                mark("Label", x, y + 12);
                mark("Icon", row.icon.getX(), row.icon.getY() - 10);
            }
            case "loot_table" -> mark("Loot table", x, y - 10);
            default -> mark("Raw JSON", x, y - 10);
        }
    }

    private int addTaskFields(int x, int y, QuestEditForms.TaskRow row) {
        if (row.useRaw) {
            row.raw.setX(x);
            row.raw.setY(y);
            addBox(row.raw);
            return y + 24;
        }
        addBox(row.id);
        switch (row.type) {
            case "item", "craft", "smelt", "pickup", "use" -> {
                addBox(row.item);
                addBox(row.count);
                if (row.type.equals("item")) {
                    row(y + 22);
                    button(row.consume ? "consume: on" : "consume: off", font.width("consume: off") + 12, b -> {
                        row.consume = !row.consume;
                        b.setMessage(Component.literal(row.consume ? "consume: on" : "consume: off"));
                    });
                    button("hand_in: " + row.handIn, font.width("hand_in: both") + 12, b -> {
                        row.handIn = switch (row.handIn) { case "both" -> "hand"; case "hand" -> "box"; default -> "both"; };
                        b.setMessage(Component.literal("hand_in: " + row.handIn));
                    });
                    button(row.teamTotal ? "team: on" : "team: off", font.width("team: off") + 12, b -> {
                        row.teamTotal = !row.teamTotal;
                        b.setMessage(Component.literal(row.teamTotal ? "team: on" : "team: off"));
                    });
                    return fy + 24;
                }
                return y + 24;
            }
            case "place", "break" -> { addBox(row.block); addBox(row.count); return y + 46; }
            case "kill" -> { addBox(row.entity); addBox(row.count); return y + 46; }
            case "dimension" -> { addBox(row.dimension); return y + 46; }
            case "biome" -> { addBox(row.biome); return y + 46; }
            case "advancement" -> { addBox(row.advancement); return y + 46; }
            case "stat" -> { addBox(row.stat); addBox(row.count); return y + 46; }
            case "checkbox" -> { addBox(row.text); return y + 46; }
            default -> {
                row.raw.setX(x);
                row.raw.setY(y + 22);
                addBox(row.raw);
                return y + 46;
            }
        }
    }

    private int addRewardFields(int x, int y, QuestEditForms.RewardRow row) {
        if (row.useRaw) {
            row.raw.setX(x);
            row.raw.setY(y);
            addBox(row.raw);
            return y + 24;
        }
        switch (row.kind) {
            case "item" -> { addBox(row.item); addBox(row.count); return y + 24; }
            case "xp", "xp_levels" -> { addBox(row.count); return y + 24; }
            case "command" -> {
                row.command.setY(y);
                addBox(row.command);
                row.label.setY(y + 22);
                addBox(row.label);
                if (row.icon.getX() + row.icon.getWidth() > right) {
                    row.icon.setX(x);
                    row.icon.setY(y + 44);
                } else row.icon.setY(y + 22);
                addBox(row.icon);
                return row.icon.getY() + 24;
            }
            case "loot_table" -> { addBox(row.loot); return y + 24; }
            default -> {
                row.raw.setX(x);
                row.raw.setY(y);
                addBox(row.raw);
                return y + 24;
            }
        }
    }

    private <T extends AbstractWidget> T body(T w) {
        addRenderableWidget(w);
        body.add(w);
        baseY.put(w, w.getY());
        return w;
    }

    private void addBox(EditBox b) {
        if (b.getX() + b.getWidth() > right) b.setWidth(Math.max(40, right - b.getX()));
        body(b);
    }

    private EditBox addField(int x, int y, int w, String value) {
        EditBox box = new EditBox(font, x, y, Math.min(w, Math.max(40, right - x)), 20, Component.literal(""));
        box.setMaxLength(10000);
        box.setValue(value == null ? "" : value);
        return body(box);
    }

    private void layoutScroll() {
        int viewH = Math.max(1, viewBottom - viewTop);
        int max = Math.max(0, contentHeight - viewH);
        if (scroll < 0) scroll = 0;
        if (scroll > max) scroll = max;
        for (AbstractWidget w : body) {
            Integer base = baseY.get(w);
            if (base == null) continue;
            int wy = base - scroll;
            w.setY(wy);
            boolean show = wy >= viewTop && wy + w.getHeight() <= viewBottom;
            w.visible = show;
            w.active = show;
        }
    }

    private boolean barHit(double mx, double my) {
        if (contentHeight <= viewBottom - viewTop) return false;
        return mx >= width - 10 && mx < width && my >= viewTop && my < viewBottom;
    }

    private void scrollTo(double mouseY) {
        int viewH = Math.max(1, viewBottom - viewTop);
        int max = Math.max(1, contentHeight - viewH);
        int barH = Math.max(16, viewH * viewH / Math.max(1, contentHeight));
        int usable = Math.max(1, viewH - barH);
        float t = ((float) mouseY - viewTop - barH / 2f) / usable;
        if (t < 0) t = 0;
        if (t > 1) t = 1;
        scroll = Math.round(t * max);
        layoutScroll();
    }

    private void commitRows() {
        JsonObject lang = new JsonObject();
        QuestEditForms.putText(draft, lang, "title", titleKey, title.getValue());
        QuestEditForms.putText(draft, lang, "subtitle", subtitleKey, subtitle.getValue());
        QuestEditForms.putText(draft, lang, "text", textKey, text.getValue());
        if (icon.getValue().isBlank()) draft.remove("icon");
        else draft.addProperty("icon", icon.getValue().trim());
        titleKey = str(draft, "title");
        subtitleKey = str(draft, "subtitle");
        textKey = str(draft, "text");
        if (lang.size() > 0) {
            JsonObject existing = draft.has("_lang") && draft.get("_lang").isJsonObject()
                    ? draft.getAsJsonObject("_lang") : new JsonObject();
            for (var e : lang.entrySet()) existing.add(e.getKey(), e.getValue());
            draft.add("_lang", existing);
        }
        JsonArray tasks = new JsonArray();
        for (QuestEditForms.TaskRow r : taskRows) tasks.add(r.toJson(lang));
        if (tasks.isEmpty()) draft.remove("tasks");
        else draft.add("tasks", tasks);
        JsonArray rewards = new JsonArray();
        for (QuestEditForms.RewardRow r : rewardRows) rewards.add(r.toJson());
        if (rewards.isEmpty()) draft.remove("rewards");
        else draft.add("rewards", rewards);
    }

    private void rebuild() {
        init();
    }

    private void save() {
        error = "";
        commitRows();
        prunePlaceholders(arr(draft, "tasks"), true);
        prunePlaceholders(arr(draft, "rewards"), false);
        if (!draft.has("tasks") || !draft.get("tasks").isJsonArray() || draft.getAsJsonArray("tasks").isEmpty())
            draft.remove("tasks");
        if (!draft.has("rewards") || !draft.get("rewards").isJsonArray() || draft.getAsJsonArray("rewards").isEmpty())
            draft.remove("rewards");
        for (QuestEditForms.TaskRow r : taskRows) {
            String err = r.validate();
            if (err != null) { error = err; return; }
        }
        for (QuestEditForms.RewardRow r : rewardRows) {
            String err = r.validate();
            if (err != null) { error = err; return; }
        }
        if (draft.has("chapter")) {
            String ch = draft.get("chapter").getAsString();
            if (ch.contains("/")) draft.addProperty("chapter", ch.substring(ch.indexOf('/') + 1));
        }
        SimpleBooks.toServerEdit.accept(new QuestEditPacket("put_quest", questId, draft.toString()));
        QuestEdit.status = "Saved " + QuestEdit.shortName(questId) + ".";
        minecraft.setScreen(parent);
    }

    private static void prunePlaceholders(JsonArray a, boolean tasks) {
        for (int i = a.size() - 1; i >= 0; i--) {
            if (!a.get(i).isJsonObject()) continue;
            JsonObject o = a.get(i).getAsJsonObject();
            if (o.size() == 0) { a.remove(i); continue; }
            if (!tasks && o.has("item") && o.get("item").getAsString().isBlank()
                    && !o.has("xp") && !o.has("xp_levels") && !o.has("command") && !o.has("loot_table") && !o.has("type"))
                a.remove(i);
            else if (tasks && !o.has("type") && !o.has("id"))
                a.remove(i);
        }
    }

    private static JsonArray arr(JsonObject o, String key) {
        if (!o.has(key) || !o.get(key).isJsonArray()) o.add(key, new JsonArray());
        return o.getAsJsonArray(key);
    }

    private static String str(JsonObject o, String key) {
        return o.has(key) && o.get(key).isJsonPrimitive() ? o.get(key).getAsString() : "";
    }

    private QuestDefs.@Nullable Quest find() {
        for (QuestDefs.Book b : QuestClientStore.defs().books().values()) {
            QuestDefs.Quest q = b.quests().get(questId);
            if (q != null) return q;
        }
        return null;
    }

    private void chrome(Gfx g) {
        int viewH = viewBottom - viewTop;
        int max = Math.max(0, contentHeight - viewH);
        if (max > 0) {
            int barH = Math.max(16, viewH * viewH / contentHeight);
            int barY = viewTop + (int) ((viewH - barH) * (scroll / (float) max));
            g.fill(width - 8, viewTop, width - 3, viewBottom, 0x55000000);
            g.fill(width - 8, barY, width - 3, barY + barH, 0xFFC4AC82);
        }
        for (int i = 0; i < labels.size(); i++) {
            int[] p = labels.get(i);
            int sy = p[1] - scroll;
            if (sy < viewTop || sy + 10 > viewBottom) continue;
            g.text(font, Component.literal(labelNames.get(i)), p[0], sy, 0xFFA0A0A0, false);
        }
        String live = error;
        if (live.isEmpty()) {
            for (QuestEditForms.TaskRow r : taskRows) {
                if (r.item.getValue().isBlank()) continue;
                String err = QuestEditForms.itemError(r.item.getValue());
                if (err != null) { live = err; break; }
            }
        }
        if (!live.isEmpty()) g.text(font, Component.literal(live), left, height - 44, 0xFFFF5555, false);
    }

    //? if >=26.1 {
    @Override
    public void extractRenderState(net.minecraft.client.gui.GuiGraphicsExtractor gg, int mouseX, int mouseY, float a) {
        super.extractRenderState(gg, mouseX, mouseY, a);
        chrome(new Gfx(gg));
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent e, boolean doubleClick) {
        if (e.button() == 0 && barHit(e.x(), e.y())) {
            barDrag = true;
            scrollTo(e.y());
            return true;
        }
        return super.mouseClicked(e, doubleClick);
    }

    @Override
    public boolean mouseDragged(net.minecraft.client.input.MouseButtonEvent e, double dx, double dy) {
        if (barDrag) {
            scrollTo(e.y());
            return true;
        }
        return super.mouseDragged(e, dx, dy);
    }

    @Override
    public boolean mouseReleased(net.minecraft.client.input.MouseButtonEvent e) {
        if (barDrag) {
            barDrag = false;
            return true;
        }
        return super.mouseReleased(e);
    }
    //?} else {
    /*@Override
    public void render(net.minecraft.client.gui.GuiGraphics gg, int mouseX, int mouseY, float a) {
        renderBackground(gg, mouseX, mouseY, a);
        super.render(gg, mouseX, mouseY, a);
        chrome(new Gfx(gg));
    }

    @Override
    public boolean mouseClicked(double x, double y, int button) {
        if (button == 0 && barHit(x, y)) {
            barDrag = true;
            scrollTo(y);
            return true;
        }
        return super.mouseClicked(x, y, button);
    }

    @Override
    public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        if (barDrag) {
            scrollTo(y);
            return true;
        }
        return super.mouseDragged(x, y, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double x, double y, int button) {
        if (barDrag) {
            barDrag = false;
            return true;
        }
        return super.mouseReleased(x, y, button);
    }
    *///?}

    //? if >=1.20.2 {
    @Override
    public boolean mouseScrolled(double x, double y, double sx, double sy) {
        if (super.mouseScrolled(x, y, sx, sy)) return true;
        scroll -= (int) Math.round(sy * 36);
        layoutScroll();
        return true;
    }
    //?} else {
    /*@Override
    public boolean mouseScrolled(double x, double y, double sy) {
        if (super.mouseScrolled(x, y, sy)) return true;
        scroll -= (int) Math.round(sy * 36);
        layoutScroll();
        return true;
    }
    *///?}

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
