package com.chipset.model;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ApiError {
    private String timestamp;
    private int status;
    private String error;
    private String message;
    private String path;
}

