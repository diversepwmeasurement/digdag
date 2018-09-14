package io.digdag.standards.operator.param;

import com.google.common.base.Optional;
import com.google.inject.Inject;
import io.digdag.client.config.Config;
import io.digdag.client.config.ConfigException;
import io.digdag.spi.Operator;
import io.digdag.spi.OperatorContext;
import io.digdag.spi.OperatorFactory;
import io.digdag.spi.TaskResult;
import io.digdag.util.BaseOperator;

import java.util.List;

public class ParamSetOperatorFactory
        implements OperatorFactory
{
    private final Config systemConfig;
    private final ParamServerClient paramServerClient;

    @Inject
    public ParamSetOperatorFactory(Config systemConfig, ParamServerClient paramServerClient)
    {
        this.systemConfig = systemConfig;
        this.paramServerClient = paramServerClient;
    }

    @Override
    public String getType()
    {
        return "param_set";
    }

    @Override
    public Operator newOperator(OperatorContext context)
    {
        return new ParamSetOperator(context, paramServerClient);
    }

    private class ParamSetOperator
            extends BaseOperator
    {
        private ParamServerClient paramServerClient;

        public ParamSetOperator(OperatorContext context, ParamServerClient paramServerClient)
        {
            super(context);
            this.paramServerClient = paramServerClient;
        }

        @Override
        public TaskResult runTask()
        {
            Optional<String> paramServerType = systemConfig.getOptional("param_server.database.type", String.class);
            if (!paramServerType.isPresent()) {
                throw new ConfigException("param_server.database.type is required to use this operator.");
            }
            Config localParams = request.getLocalConfig();

            List<String> keys = localParams.getKeys();
            if (keys.size() == 0) {
                throw new ConfigException("no key is set.");
            }

            paramServerClient.doTransaction(client -> {
                for (String key : keys) {
                    // This operator expected to take a String like scalar value as parameters.
                    // e.g.
                    //   param_set>:
                    //   key1: value1
                    //   key2: value2
                    //
                    // So if user specified Array like value, `localParams.getOptional(key, String.class)` throws Error.
                    // If we about to support Array like value, we need to create an another operator.
                    Optional<String> value = localParams.getOptional(key, String.class);
                    if (value.isPresent()) {
                        paramServerClient.set(key, value.get(), request.getSiteId());
                    }
                }
            });

            return TaskResult.empty(request);
        }
    }
}
