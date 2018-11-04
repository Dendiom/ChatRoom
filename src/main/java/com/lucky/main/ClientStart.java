package com.lucky.main;

import com.lucky.Client;

public class ClientStart {

    public static void main(String[] args) {
        Client client = new Client();
        client.connect();
        client.startGetUserInput();
    }
}
