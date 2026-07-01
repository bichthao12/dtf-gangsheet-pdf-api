package com.example.dtfgangsheet.commerce.payload;

import com.example.dtfgangsheet.model.GangSheetSnapshot;
import com.example.dtfgangsheet.model.LinePayload;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.Map;

public final class BuilderLinePayloadMapper {

    private BuilderLinePayloadMapper() {
    }

    public static LinePayload orderPayload(String designId, GangSheetSnapshot snapshot, ObjectMapper mapper) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put(BuilderLinePayloadKeys.DESIGN_ID, designId);
        data.put(BuilderLinePayloadKeys.SNAPSHOT, mapper.convertValue(snapshot, Map.class));
        return LinePayload.of(data);
    }
}
