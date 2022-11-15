package dev.rinaldo.quarkusoperatorsdk;

import dev.rinaldo.quarkusoperatorsdk.customresource.Aplicacao;
import dev.rinaldo.quarkusoperatorsdk.customresource.AplicacaoStatus;
import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentBuilder;
import io.fabric8.kubernetes.api.model.apps.DeploymentCondition;
import io.fabric8.kubernetes.api.model.networking.v1.Ingress;
import io.fabric8.kubernetes.api.model.networking.v1.IngressBuilder;
import io.fabric8.kubernetes.api.model.networking.v1.IngressStatus;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import io.quarkus.logging.Log;

import javax.inject.Inject;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static io.javaoperatorsdk.operator.api.reconciler.Constants.WATCH_CURRENT_NAMESPACE;

@ControllerConfiguration(namespaces = WATCH_CURRENT_NAMESPACE, name = "aplicacao")
public class AplicacaoReconciler implements Reconciler<Aplicacao> {

    public static final Duration INTERVALO_TENTATIVA = Duration.ofSeconds(3);
    private static final String APP_LABEL = "app.kubernetes.io/name";

    @Inject
    KubernetesClient kubernetesClient;

    @Override
    public UpdateControl<Aplicacao> reconcile(Aplicacao aplicacao, Context<Aplicacao> context) {
        final ObjectMeta metadata = getMetadataPadrao(aplicacao);

        final Deployment deployment = criaDeployment(aplicacao, metadata);
        if (deployment == null) {
            return novaTentativaReconciliacao("deployment", metadata);
        }

        final Service service = criaService(aplicacao, metadata);
        if (service == null) {
            return novaTentativaReconciliacao("service", metadata);
        }

        final Ingress ingress = criaIngress(aplicacao, metadata);
        if (ingress == null) {
            return novaTentativaReconciliacao("ingress", metadata);
        }

        final String url = extraiURL(ingress);

        Log.infof("Aplicacao %s criada e exposta com sucesso em %s", metadata.getName(), url);
        aplicacao.setStatus(new AplicacaoStatus(url));
        return UpdateControl.updateStatus(aplicacao);
    }

    private UpdateControl<Aplicacao> novaTentativaReconciliacao(String recurso, ObjectMeta metadata) {
        Log.infof("O %s da aplicacao %s ainda não está pronto. Reagendando nova tentativa de reconciliacao após {}s",
                recurso, metadata.getName(), INTERVALO_TENTATIVA.toSeconds());
        return UpdateControl.<Aplicacao>noUpdate().rescheduleAfter(INTERVALO_TENTATIVA);
    }

    private ObjectMeta getMetadataPadrao(Aplicacao aplicacao) {
        final String nome = aplicacao.getMetadata().getName();
        final String namespace = aplicacao.getMetadata().getNamespace();
        Map<String, String> labels = Map.of(APP_LABEL, nome);
        ObjectMeta metadata = new ObjectMetaBuilder()
                .withName(nome)
                .withNamespace(namespace)
                .withLabels(labels)
                .build();
        return metadata;
    }

    private Deployment criaDeployment(Aplicacao aplicacao, ObjectMeta metadata) {
        Deployment deployAtual = kubernetesClient.apps().deployments()
                .inNamespace(metadata.getNamespace())
                .withName(metadata.getName())
                .get();

        if (deployAtual == null) {
            List<EnvVar> envVars = aplicacao.getSpec().getVariaveisDeAmbiente()
                    .entrySet()
                    .stream()
                    .map(entry -> {
                            var envVar = new EnvVar();
                            envVar.setName(entry.getKey());
                            envVar.setValue(entry.getValue());
                            return envVar;
                        })
                    .collect(Collectors.toList());

            var novoDeploy = new DeploymentBuilder()
                    .withMetadata(metadata)
                    .withNewSpec()
                        .withNewSelector().withMatchLabels(metadata.getLabels()).endSelector()
                        .withNewTemplate()
                            .withNewMetadata().withLabels(metadata.getLabels())
                            .endMetadata()
                            .withNewSpec()
                                .addNewContainer()
                                    .withName(aplicacao.getMetadata().getName())
                                    .withImage(aplicacao.getSpec().getImagem())
                                    .addAllToEnv(envVars)
                                    .addNewPort()
                                        .withName("http")
                                        .withProtocol("TCP")
                                        .withContainerPort(8080)
                                    .endPort()
                                .endContainer()
                            .endSpec()
                        .endTemplate()
                    .endSpec()
                    .build();

            deployAtual = kubernetesClient.apps().deployments().resource(novoDeploy).create();
        }

        Predicate<DeploymentCondition> condicaoDeployCompleto = cond ->
                    cond.getType().equals("Progressing") &&
                    cond.getStatus().equals("True") &&
                    cond.getReason().equals("NewReplicaSetAvailable");
        if (deployAtual.getStatus().getConditions().stream().anyMatch(condicaoDeployCompleto)) {
            return deployAtual;
        }
        return null;
    }

    private Service criaService(Aplicacao aplicacao, ObjectMeta metadata) {
        Service serviceAtual = kubernetesClient.services()
                .inNamespace(metadata.getNamespace())
                .withName(metadata.getName())
                .get();

        if (serviceAtual == null) {
            var novoService = new ServiceBuilder()
                    .withMetadata(metadata)
                    .withNewSpec()
                        .addNewPort()
                        .withName("http")
                        .withPort(8080)
                        .withNewTargetPort().withValue(8080).endTargetPort()
                        .endPort()
                        .withSelector(metadata.getLabels())
                        .withType("ClusterIP")
                    .endSpec()
                    .build();

            serviceAtual = kubernetesClient.services().resource(novoService).create();
        }

        if (serviceAtual != null) {
            return serviceAtual;
        }
        return null;
    }

    private Ingress criaIngress(Aplicacao aplicacao, ObjectMeta metadata) {
        Ingress ingressAtual = kubernetesClient.resources(Ingress.class)
                .inNamespace(metadata.getNamespace())
                .withName(metadata.getName())
                .get();

        if (ingressAtual == null) {
            var novoIngress = new IngressBuilder()
                    .withMetadata(metadata)
                    .withNewSpec()
                        .addNewRule()
                            .withNewHttp()
                                .addNewPath()
                                    .withPath("/")
                                    .withPathType("Prefix")
                                    .withNewBackend()
                                    .withNewService()
                                    .withName(metadata.getName())
                                    .withNewPort().withNumber(8080).endPort()
                                    .endService()
                                    .endBackend()
                                .endPath()
                            .endHttp()
                        .endRule()
                    .endSpec()
                    .build();

            ingressAtual = kubernetesClient.resources(Ingress.class).resource(novoIngress).create();
        }

        if (ingressAtual != null) {
            IngressStatus status = ingressAtual.getStatus();
            if (status != null) {
                List<LoadBalancerIngress> ingress = status.getLoadBalancer().getIngress();
                if (ingress != null && !ingress.isEmpty()) {
                    return ingressAtual;
                }
            }
        }

        return null;
    }

    private String extraiURL(Ingress ingress) {
        LoadBalancerIngress ingressLB = ingress.getStatus().getLoadBalancer().getIngress().get(0);
        final String hostname = ingressLB.getHostname();
        final String url = "https://" + (hostname != null ? hostname : ingressLB.getIp());
        return url;
    }

}
