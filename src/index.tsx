// prettier-ignore
/* eslint-disable @typescript-eslint/no-unused-vars */
/* eslint-disable react-hooks/exhaustive-deps */
import React from 'react';
import {
  NativeModules,
  Platform,
  DeviceEventEmitter,
  NativeEventEmitter,
} from 'react-native';
// import type { EmitterSubscription } from 'react-native'
const LINKING_ERROR =
  `The package 'react-native-rabbitmq' doesn't seem to be linked. Make sure: \n\n` +
  Platform.select({ ios: "- You have run 'pod install'\n", default: '' }) +
  '- You rebuilt the app after installing the package\n' +
  '- You are not using Expo Go\n';

const Rabbitmq = NativeModules.Rabbitmq
  ? NativeModules.Rabbitmq
  : new Proxy(
      {},
      {
        get() {
          throw new Error(LINKING_ERROR);
        },
      }
    );

export function multiply(x: number, y: number) {
  return x;
}
const EventEmitter = new NativeEventEmitter(Rabbitmq);
interface RabbitMQConfig {
  username: string;
  password: string;
  host: string;
  port: string;
  virtualhost: string;
  ssl: boolean;
}

interface RabbitMQEvent {
  name: string;
  // Define other properties as needed
}

// interface RabbitmqModule {
//   initialize(config: RabbitMQConfig): void;
//   connect(): void;
//   close(): void;
// }

interface RabbitMQProps {
  config: RabbitMQConfig;
}

export const RabbitMQ: React.FC<RabbitMQProps> = ({ config }) => {
  const [connected, setConnected] = React.useState<boolean>(false);
  const [callbacks, setCallbacks] = React.useState<{
    [eventName: string]: (event: RabbitMQEvent) => void;
  }>({});

  React.useEffect(() => {
    Rabbitmq.initialize(config);
    const subscription = EventEmitter.addListener(
      'RabbitMqConnectionEvent',
      handleEvent
    );

    return () => {
      subscription.remove();
    };
  }, []);

  const handleEvent = (event: RabbitMQEvent) => {
    if (event.name === 'connected') {
      setConnected(true);
    }

    if (callbacks[event.name]) {
      callbacks[event.name](event);
    }
  };

  const connect = () => {
    Rabbitmq.connect();
  };

  const close = () => {
    Rabbitmq.close();
  };

  const on = (event: string, callback: (event: RabbitMQEvent) => void) => {
    setCallbacks((prevCallbacks) => ({
      ...prevCallbacks,
      [event]: callback,
    }));
  };

  const removeOn = (event: string) => {
    setCallbacks((prevCallbacks) => {
      const updatedCallbacks = { ...prevCallbacks };
      delete updatedCallbacks[event];
      return updatedCallbacks;
    });
  };

  return null; // Replace with your desired UI or return meaningful component content
};

interface RabbitMqConnection {
  addExchange(config: any): void;
  publishToExchange(
    message: string,
    exchange_name: string,
    routing_key: string,
    message_properties: any
  ): void;
  deleteExchange(exchange_name: string): void;
}

interface ExchangeConfig {
  name: string;
  // Add other exchange configuration options as needed
}

interface RabbitMqExchangeEvent {
  name: string;
  exchange_name: string;
  // Define other event properties as needed
}

export class Exchange {
  private rabbitmqconnection: RabbitMqConnection;
  private callbacks: {
    [eventName: string]: (event: RabbitMqExchangeEvent) => void;
  } = {};

  public name: string;
  public exchange_config: ExchangeConfig;

  constructor(
    connection: { rabbitmqconnection: RabbitMqConnection },
    exchange_config: ExchangeConfig
  ) {
    this.rabbitmqconnection = connection.rabbitmqconnection;
    this.name = exchange_config.name;
    this.exchange_config = exchange_config;

    this.rabbitmqconnection.addExchange(exchange_config);
    this.setupEventListeners();
  }

  private setupEventListeners() {
    DeviceEventEmitter.addListener('RabbitMqExchangeEvent', this.handleEvent);
  }

  private handleEvent = (event: RabbitMqExchangeEvent) => {
    if (
      event.name !== 'RabbitMqExchangeEvent' ||
      event?.exchange_name !== this.name
    ) {
      return;
    }

    if (this.callbacks[event.name]) {
      this.callbacks[event.name](event);
    }
  };

  public on(event: string, callback: (event: RabbitMqExchangeEvent) => void) {
    this.callbacks[event] = callback;
  }

  public removeOn(event: string) {
    delete this.callbacks[event];
  }

  public publish(
    message: string,
    routing_key: string = '',
    properties: any = {}
  ) {
    this.rabbitmqconnection.publishToExchange(
      message,
      this.name,
      routing_key,
      properties
    );
  }

  public delete() {
    this.rabbitmqconnection.deleteExchange(this.name);
    this.removeEventListeners();
  }

  private removeEventListeners() {
    DeviceEventEmitter.removeAllListeners('RabbitMqExchangeEvent');
  }
}

//=================================

const RabbitMqEmitter = new NativeEventEmitter();

interface RabbitMqQueueEvent {
  name: string;
  queue_name: string;
  delivery_tag?: number;
  // Define other event properties as needed
}

interface QueueConfig {
  name: string;
  autoBufferAck?: boolean;
  buffer_delay?: number;
  [key: string]: any;
  // Add other configuration options as needed
}

interface RabbitMqConnection {
  initialize(config: any): void;
  addQueue(queue_config: any, option: any): void;
  bindQueue(
    exchange_name: string,
    queue_name: string,
    routing_key: string
  ): void;
  unbindQueue(
    exchange_name: string,
    queue_name: string,
    routing_key: string
  ): void;
  removeQueue(queue_name: string): void;
  basicAck(queue_name: string, delivery_tag: number): void;
}

interface QueueProps {
  connection: { rabbitmqconnection: RabbitMqConnection };
  queueConfig: QueueConfig;
  option?: any;
}

export const Queue: React.FC<QueueProps> = ({
  connection,
  queueConfig,
  option,
}) => {
  const [callbacks, setCallbacks] = React.useState<{
    [eventName: string]: (
      event: RabbitMqQueueEvent | RabbitMqQueueEvent[]
    ) => void;
  }>({});
  const [messageBuffer, setMessageBuffer] = React.useState<
    RabbitMqQueueEvent[]
  >([]);
  const [messageBufferTimeout, setMessageBufferTimeout] =
    React.useState<any>(null);

  React.useEffect(() => {
    const subscription = RabbitMqEmitter.addListener(
      'RabbitMqQueueEvent',
      handleEvent
    );
    connection.rabbitmqconnection.addQueue(queueConfig, option);

    return () => {
      subscription.remove();
    };
  }, []);

  const handleEvent = (event: RabbitMqQueueEvent) => {
    if (event.queue_name !== queueConfig.name) {
      return;
    }

    if (event.name === 'message') {
      if (queueConfig.autoBufferAck) {
        basicAck(event.delivery_tag || 0);
      }

      if (callbacks.message) {
        callbacks.message(event);
      }

      if (callbacks.messages) {
        setMessageBuffer((prevBuffer) => [...prevBuffer, event]);

        if (messageBufferTimeout) {
          clearTimeout(messageBufferTimeout);
        }

        setMessageBufferTimeout(
          setTimeout(() => {
            if (messageBuffer.length > 0 && callbacks.messages) {
              callbacks.messages(messageBuffer);
              setMessageBuffer([]);
            }
          }, queueConfig.buffer_delay || 1000)
        );
      }
    } else if (callbacks[event.name]) {
      callbacks[event.name](event);
    }
  };

  const on = (event: string, callback: (event: RabbitMqQueueEvent) => void) => {
    setCallbacks((prevCallbacks) => ({
      ...prevCallbacks,
      [event]: callback,
    }));
  };

  const removeOn = (event: string) => {
    setCallbacks((prevCallbacks) => {
      const updatedCallbacks = { ...prevCallbacks };
      delete updatedCallbacks[event];
      return updatedCallbacks;
    });
  };

  const bind = (exchange: { name: string }, routing_key: string = '') => {
    connection.rabbitmqconnection.bindQueue(
      exchange.name,
      queueConfig.name,
      routing_key
    );
  };

  const unbind = (exchange: { name: string }, routing_key: string = '') => {
    connection.rabbitmqconnection.unbindQueue(
      exchange.name,
      queueConfig.name,
      routing_key
    );
  };

  const deleteQueue = () => {
    if (queueConfig.name !== '') {
      connection.rabbitmqconnection.removeQueue(queueConfig.name);
    }
  };

  const basicAck = (delivery_tag: number) => {
    connection.rabbitmqconnection.basicAck(queueConfig.name, delivery_tag);
  };

  const close = () => {
    setMessageBuffer([]);
    if (messageBufferTimeout) {
      clearTimeout(messageBufferTimeout);
    }
    setCallbacks({});
  };

  return null; // Replace with your desired UI or return meaningful component content
};
