package com.moonevue.gateway.controller;

import com.moonevue.core.entity.BankConfiguration;
import com.moonevue.gateway.mtls.MutualTlsHttpService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/mtls")
public class MtlsDemoController {

    private final MutualTlsHttpService service = new MutualTlsHttpService();

    // Ex.: GET http://localhost:8080/mtls/get?url=https://api.alvo.com/recurso&certPath=/opt/certs/cli.p12&certPassword=senha
    @GetMapping("/get")
    public String doGet(@RequestParam String url,
                        @RequestParam String certPath,
                        @RequestParam String certPassword) {
        BankConfiguration b = new BankConfiguration(certPath, certPassword);
        return service.get(b, url, Map.of());
    }

    // Ex.: POST http://localhost:8080/mtls/post?url=https://api.alvo.com/recurso&certPath=/opt/certs/cli.p12&certPassword=senha
    // Body: JSON puro
    @PostMapping("/post")
    public String doPost(@RequestParam String url,
                         @RequestParam String certPath,
                         @RequestParam String certPassword,
                         @RequestBody(required = false) String body) {
        BankConfiguration b = new BankConfiguration(certPath, certPassword);
        return service.postJson(b, url, body, Map.of());
    }
}
