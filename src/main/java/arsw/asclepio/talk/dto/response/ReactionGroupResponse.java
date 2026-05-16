package arsw.asclepio.talk.dto.response;

import arsw.asclepio.talk.domain.message.MessageReaction;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

// Reacciones agrupadas por emoji para que el cliente las pinte como chips.
// userIds permite saber si el usuario actual ya reaccionó con ese emoji
// (toggle visual) sin pedir el detalle por separado.
// Daniel Useche
public record ReactionGroupResponse(
        String emoji,
        int count,
        List<UUID> userIds,
        List<String> userNames
) {
    // Agrupa una lista de reacciones por emoji preservando orden de aparición.
    // LinkedHashMap mantiene el orden de inserción, lo que da estabilidad al
    // renderizado en el cliente (no salta el orden de los chips entre fetches).
    public static List<ReactionGroupResponse> groupBy(List<MessageReaction> reactions) {
        Map<String, List<MessageReaction>> grouped = reactions.stream()
                .collect(Collectors.groupingBy(MessageReaction::getEmoji, LinkedHashMap::new, Collectors.toList()));
        return grouped.entrySet().stream()
                .map(e -> new ReactionGroupResponse(
                        e.getKey(),
                        e.getValue().size(),
                        e.getValue().stream().map(MessageReaction::getUserId).toList(),
                        e.getValue().stream().map(MessageReaction::getUserName).toList()
                ))
                .toList();
    }
}
