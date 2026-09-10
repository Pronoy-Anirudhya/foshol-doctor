package com.rootcause.foshol.identity.application.port;

public interface PhoneCipherPort {

    byte[] encrypt(String e164);
}
