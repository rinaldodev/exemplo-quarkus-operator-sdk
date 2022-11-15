package dev.rinaldo.quarkusoperatorsdk.customresource;

import io.fabric8.kubernetes.api.model.Namespaced;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.model.annotation.Group;
import io.fabric8.kubernetes.model.annotation.Version;

@Version("v1alpha1")
@Group("rinaldo.dev")
public class Aplicacao extends CustomResource<AplicacaoSpec, AplicacaoStatus> implements Namespaced {

    @Override
    protected AplicacaoSpec initSpec() {
        return new AplicacaoSpec();
    }

    @Override
    protected AplicacaoStatus initStatus() {
        return new AplicacaoStatus();
    }
}
