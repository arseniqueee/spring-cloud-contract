/*
 * Copyright 2013-2020 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.contract.verifier.messaging.stream;

import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.contract.verifier.messaging.MessageVerifier;
import org.springframework.cloud.contract.verifier.messaging.internal.ContractVerifierMessage;
import org.springframework.cloud.contract.verifier.messaging.internal.ContractVerifierMessaging;
import org.springframework.cloud.contract.verifier.messaging.noop.NoOpContractVerifierAutoConfiguration;
import org.springframework.cloud.stream.binder.Binder;
import org.springframework.cloud.stream.binder.test.InputDestination;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.util.Assert;

/**
 * @author Marcin Grzejszczak
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(Binder.class)
@ConditionalOnProperty(name = "stubrunner.stream.enabled", havingValue = "true", matchIfMissing = true)
@AutoConfigureBefore(NoOpContractVerifierAutoConfiguration.class)
public class ContractVerifierStreamAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean
	public ContractVerifierMessaging<?> contractVerifierMessagingConverter(MessageVerifier<Message<?>> exchange) {
		return new ContractVerifierHelper(exchange);
	}

	@Configuration(proxyBeanMethods = false)
	@ConditionalOnClass(InputDestination.class)
	static class InputDestinationConfiguration {

		@Bean
		@ConditionalOnMissingBean
		MessageVerifier<Message<?>> contractVerifierMessageExchangeWithDestinations(ApplicationContext context) {
			return new StreamStubMessages(new StreamInputDestinationMessageSender(context),
					new StreamOutputDestinationMessageReceiver(context));
		}

	}

	@Configuration(proxyBeanMethods = false)
	@ConditionalOnMissingClass({ "org.springframework.cloud.stream.binder.test.InputDestination" })
	static class NoOpStreamClassConfiguration {

		@Bean
		@ConditionalOnMissingBean
		MessageVerifier<Message<?>> contractVerifierMessageExchangeWithNoMessageCollector(
				ApplicationContext applicationContext) {
			return new StreamStubMessages(new StreamStubMessageSender(applicationContext),
					new StreamPollableChannelMessageReceiver(applicationContext));
		}

	}

}

class ContractVerifierHelper extends ContractVerifierMessaging<Message<?>> {

	ContractVerifierHelper(MessageVerifier<Message<?>> exchange) {
		super(exchange);
	}

	@Override
	protected ContractVerifierMessage convert(Message<?> receive) {
		Assert.notNull(receive, "Message must not be null!");
		return new ContractVerifierMessage(receive.getPayload(), receive.getHeaders());
	}

}
