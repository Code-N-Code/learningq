package com.codencode.learningq.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class TokenPurposeConverter implements AttributeConverter<TokenPurpose, String> {
    @Override
    public String convertToDatabaseColumn(TokenPurpose value) {
        return value == null ? null : value.databaseValue();
    }

    @Override
    public TokenPurpose convertToEntityAttribute(String value) {
        if (value == null) return null;
        for (TokenPurpose purpose : TokenPurpose.values()) {
            if (purpose.databaseValue().equals(value)) return purpose;
        }
        throw new IllegalArgumentException("Unknown authentication token purpose");
    }
}
