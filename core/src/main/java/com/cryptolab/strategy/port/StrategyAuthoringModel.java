package com.cryptolab.strategy.port;

import com.cryptolab.strategy.domain.StrategyPluginDescriptor;
import java.util.List;

public interface StrategyAuthoringModel {

    String proposeIdea(String prompt, List<StrategyPluginDescriptor> availableStrategies);

    String generateJson(
            String prompt,
            String confirmedIdea,
            List<StrategyPluginDescriptor> availableStrategies,
            String previousOutput,
            String validationError);
}
