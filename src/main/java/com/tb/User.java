package com.tb;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class User {

    String name;

    String IP;

    int port;

    String status;
}
