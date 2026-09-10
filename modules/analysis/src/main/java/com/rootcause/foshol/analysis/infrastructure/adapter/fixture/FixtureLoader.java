package com.rootcause.foshol.analysis.infrastructure.adapter.fixture;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rootcause.foshol.analysis.application.port.SidecarFailureException;
import com.rootcause.foshol.common.contract.ErrorCodes;
import java.io.IOException;
import java.io.InputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;

final class FixtureLoader {

    private static final Logger log = LoggerFactory.getLogger(FixtureLoader.class);

    private FixtureLoader() {}

    static JsonNode json(ObjectMapper mapper, String folder, String sha256) {
        String path = "fixtures/" + folder + "/" + sha256 + ".json";
        try (InputStream in = resource(path)) {
            return mapper.readTree(in);
        } catch (IOException ex) {
            throw missing(path, sha256, ex);
        }
    }

    static byte[] bytes(String folder, String sha256, String extension) {
        String path = "fixtures/" + folder + "/" + sha256 + "." + extension;
        try (InputStream in = resource(path)) {
            return in.readAllBytes();
        } catch (IOException ex) {
            throw missing(path, sha256, ex);
        }
    }

    private static InputStream resource(String path) throws IOException {
        ClassPathResource resource = new ClassPathResource(path);
        if (!resource.exists()) {
            throw missing(path, path, null);
        }
        return resource.getInputStream();
    }

    private static SidecarFailureException missing(String path, String key, Throwable cause) {
        log.warn("fixture missing path={} key={}", path, key);
        return new SidecarFailureException(ErrorCodes.ERR_FIXTURE_MISSING, "Fixture missing: " + path, cause);
    }
}
