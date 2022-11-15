package dev.rinaldo.quarkusoperatorsdk.customresource;

import java.util.Collections;
import java.util.Map;

public class AplicacaoSpec {

    private String imagem;
    private Map<String, String> variaveisDeAmbiente;

    public String getImagem() {
        return imagem;
    }

    public void setImagem(String imagem) {
        this.imagem = imagem;
    }

    public Map<String, String> getVariaveisDeAmbiente() {
        return variaveisDeAmbiente == null ? Collections.emptyMap() : variaveisDeAmbiente;
    }

}
