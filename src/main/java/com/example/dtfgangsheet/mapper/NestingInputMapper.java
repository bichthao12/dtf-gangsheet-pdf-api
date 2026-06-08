package com.example.dtfgangsheet.mapper;

import com.example.dtfgangsheet.dto.request.NestingRequest;
import com.example.dtfgangsheet.model.NestingInput;

import java.util.List;

public final class NestingInputMapper {

    private NestingInputMapper() {
    }

    public static NestingInput toModel(NestingRequest request) {
        if (request == null) {
            return null;
        }

        return new NestingInput(
                request.img(),
                request.width(),
                request.height(),
                request.quantity()
        );
    }

    public static List<NestingInput> toModels(List<NestingRequest> requests) {
        if (requests == null) {
            return List.of();
        }

        return requests.stream()
                .map(NestingInputMapper::toModel)
                .toList();
    }
}