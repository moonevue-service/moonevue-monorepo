package com.moonevue.gateway.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PaymentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void createPayment_deveria_retornar_401_sem_autenticacao() throws Exception {
        var body = Map.of("bank", "EFI", "bankConfigurationId", 1);

        mockMvc.perform(post("/payments")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createPixImmediate_deveria_retornar_401_sem_autenticacao() throws Exception {
        mockMvc.perform(post("/payments/pix/immediate")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createBoleto_deveria_retornar_401_sem_autenticacao() throws Exception {
        mockMvc.perform(post("/payments/boleto")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void emitChargeForTransaction_deveria_retornar_401_sem_autenticacao() throws Exception {
        mockMvc.perform(post("/payments/v1/transactions/1/charges/emit")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void listChargesByTransaction_deveria_retornar_401_sem_autenticacao() throws Exception {
        mockMvc.perform(get("/payments/v1/transactions/1/charges"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void retryChargeForTransaction_deveria_retornar_401_sem_autenticacao() throws Exception {
        mockMvc.perform(post("/payments/v1/transactions/1/charges/retry")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isUnauthorized());
    }
}
