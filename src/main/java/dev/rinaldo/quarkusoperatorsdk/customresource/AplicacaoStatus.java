package dev.rinaldo.quarkusoperatorsdk.customresource;

public class AplicacaoStatus {

    private String host;
    private String mensagem;

    private long espera = System.currentTimeMillis();
    private boolean pronta = false;

    public AplicacaoStatus() {
        mensagem = "processando";
    }

    public AplicacaoStatus(String hostname) {
        this.mensagem = "pronta";
        this.host = hostname;
        pronta = true;
        espera = System.currentTimeMillis() - espera;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public String getMensagem() {
        return mensagem;
    }

    public void setMensagem(String mensagem) {
        this.mensagem = mensagem;
    }

    public long getEspera() {
        if (!pronta) {
            espera = System.currentTimeMillis() - espera;
        }
        return espera;
    }

    public void setEspera(long espera) {
        this.espera = espera;
    }
}
