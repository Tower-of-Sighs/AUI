(function() {
  var root = typeof globalThis !== 'undefined' ? globalThis : this;
  var host;

  if (typeof root.window === 'undefined') root.window = root;
  if (typeof root.globalThis === 'undefined') root.globalThis = root;
  if (typeof root.self === 'undefined') root.self = root;
  host = typeof window !== 'undefined' && window ? window : (root.window || root);

  var hostQueueMicrotask = typeof host.queueMicrotask === 'function'
    ? host.queueMicrotask
    : null;
  var hostSetTimeout = typeof host.setTimeout === 'function'
    ? host.setTimeout
    : (typeof root.setTimeout === 'function' ? root.setTimeout : null);

  if (typeof root.queueMicrotask !== 'function') {
    root.queueMicrotask = function(callback) {
      if (typeof callback !== 'function') throw new TypeError('callback must be a function');
      if (hostQueueMicrotask) return hostQueueMicrotask.call(host, callback);
      if (hostSetTimeout) return hostSetTimeout.call(host, callback, 0);
      throw new Error('No asynchronous callback scheduler is available');
    };
  }

  function enqueue(callback) {
    return root.queueMicrotask.call(root, callback);
  }

  var isObject = function(value) {
    return value !== null && (typeof value === 'object' || typeof value === 'function');
  };

  if (typeof root.Uint8Array !== 'function') {
    root.Uint8Array = function(input) {
      var result = [];
      var length;
      var index;
      if (typeof input === 'number') {
        length = Math.max(0, Math.floor(input));
        for (index = 0; index < length; index++) result[index] = 0;
        return result;
      }
      length = input == null ? 0 : Number(input.length) || 0;
      for (index = 0; index < length; index++) result[index] = Number(input[index]) & 255;
      return result;
    };
  }

  if (typeof root.Promise !== 'function') {
    var settle = function(promise, state, value) {
      var handlers;
      var index;
      if (promise._state !== 0) return;
      promise._state = state;
      promise._value = value;
      handlers = promise._handlers;
      promise._handlers = [];
      for (index = 0; index < handlers.length; index++) {
        scheduleHandler(promise, handlers[index]);
      }
    };

    var resolvePromise = function(promise, value) {
      var then;
      if (value === promise) {
        settle(promise, 2, new TypeError('A promise cannot resolve itself'));
        return;
      }
      if (!isObject(value)) {
        settle(promise, 1, value);
        return;
      }
      try {
        then = value.then;
      } catch (error) {
        settle(promise, 2, error);
        return;
      }
      if (typeof then !== 'function') {
        settle(promise, 1, value);
        return;
      }
      enqueue(function() {
        var called = false;
        function resolveThenable(nextValue) {
          if (called) return;
          called = true;
          resolvePromise(promise, nextValue);
        }
        function rejectThenable(reason) {
          if (called) return;
          called = true;
          settle(promise, 2, reason);
        }
        try {
          then.call(value, resolveThenable, rejectThenable);
        } catch (error) {
          if (!called) settle(promise, 2, error);
        }
      });
    };

    var scheduleHandler = function(promise, handler) {
      enqueue(function() {
        var callback = promise._state === 1 ? handler.onFulfilled : handler.onRejected;
        var value;
        try {
          if (typeof callback !== 'function') {
            if (promise._state === 1) handler.resolve(promise._value);
            else handler.reject(promise._value);
            return;
          }
          value = callback(promise._value);
          handler.resolve(value);
        } catch (error) {
          handler.reject(error);
        }
      });
    };

    var PromisePolyfill = function(executor) {
      var promise = this;
      var resolved = false;
      if (!(promise instanceof PromisePolyfill)) throw new TypeError('Promises must be constructed');
      if (typeof executor !== 'function') throw new TypeError('Promise resolver is not a function');
      promise._state = 0;
      promise._value = undefined;
      promise._handlers = [];
      function resolve(value) {
        if (resolved) return;
        resolved = true;
        resolvePromise(promise, value);
      }
      function reject(reason) {
        if (resolved) return;
        resolved = true;
        settle(promise, 2, reason);
      }
      try {
        executor(resolve, reject);
      } catch (error) {
        reject(error);
      }
    };

    PromisePolyfill.prototype.then = function(onFulfilled, onRejected) {
      var parent = this;
      var child = new PromisePolyfill(function(resolve, reject) {
        var handler = {
          onFulfilled: onFulfilled,
          onRejected: onRejected,
          resolve: resolve,
          reject: reject
        };
        if (parent._state === 0) parent._handlers.push(handler);
        else scheduleHandler(parent, handler);
      });
      return child;
    };

    PromisePolyfill.prototype['catch'] = function(onRejected) {
      return this.then(null, onRejected);
    };

    PromisePolyfill.prototype['finally'] = function(onFinally) {
      var promise = this;
      var constructor;
      if (typeof onFinally !== 'function') return promise.then();
      constructor = promise.constructor;
      if (typeof constructor !== 'function') constructor = PromisePolyfill;
      return promise.then(function(value) {
        return constructor.resolve(onFinally()).then(function() { return value; });
      }, function(reason) {
        return constructor.resolve(onFinally()).then(function() { throw reason; });
      });
    };

    PromisePolyfill.resolve = function(value) {
      var constructor = this;
      if (value instanceof PromisePolyfill && value.constructor === constructor) return value;
      return new constructor(function(resolve) { resolve(value); });
    };

    PromisePolyfill.reject = function(reason) {
      var constructor = this;
      return new constructor(function(resolve, reject) { reject(reason); });
    };

    var iterableValues = function(iterable) {
      var values = [];
      var iteratorKey = root.Symbol && root.Symbol.iterator;
      var iterator;
      var step;
      var length;
      var index;
      if (iterable == null) throw new TypeError('Value is not iterable');
      if (iteratorKey && typeof iterable[iteratorKey] === 'function') {
        iterator = iterable[iteratorKey]();
        if (!iterator || typeof iterator.next !== 'function') throw new TypeError('Value is not iterable');
        while (true) {
          step = iterator.next();
          if (step.done) break;
          values.push(step.value);
        }
        return values;
      }
      length = Number(iterable.length);
      if (isNaN(length) || length < 0 || length === Infinity) throw new TypeError('Value is not iterable');
      length = Math.floor(length);
      for (index = 0; index < length; index++) values.push(iterable[index]);
      return values;
    };

    PromisePolyfill.all = function(iterable) {
      var constructor = this;
      return new constructor(function(resolve, reject) {
        var items = iterableValues(iterable);
        var results = [];
        var remaining = items.length;
        var index;
        if (remaining === 0) {
          resolve(results);
          return;
        }
        function fulfillAt(position, value) {
          results[position] = value;
          remaining--;
          if (remaining === 0) resolve(results);
        }
        for (index = 0; index < items.length; index++) {
          (function(position) {
            constructor.resolve(items[position]).then(function(value) {
              fulfillAt(position, value);
            }, reject);
          })(index);
        }
      });
    };

    PromisePolyfill.race = function(iterable) {
      var constructor = this;
      return new constructor(function(resolve, reject) {
        var items = iterableValues(iterable);
        var index;
        for (index = 0; index < items.length; index++) {
          constructor.resolve(items[index]).then(resolve, reject);
        }
      });
    };

    root.Promise = PromisePolyfill;
  }

  if (!root.Reflect) root.Reflect = {};
  if (typeof root.Reflect.get !== 'function') {
    root.Reflect.get = function(target, propertyKey, receiver) {
      var object = target;
      var descriptor;
      var actualReceiver = receiver === undefined ? target : receiver;
      while (object !== null) {
        descriptor = Object.getOwnPropertyDescriptor(object, propertyKey);
        if (descriptor) {
          if (Object.prototype.hasOwnProperty.call(descriptor, 'value')) return descriptor.value;
          return typeof descriptor.get === 'function' ? descriptor.get.call(actualReceiver) : undefined;
        }
        object = Object.getPrototypeOf(object);
      }
      return undefined;
    };
  }
  if (typeof root.Reflect.set !== 'function') {
    root.Reflect.set = function(target, propertyKey, value, receiver) {
      var actualReceiver = receiver === undefined ? target : receiver;
      var object = target;
      var descriptor;
      var receiverDescriptor;
      try {
        while (object !== null) {
          descriptor = Object.getOwnPropertyDescriptor(object, propertyKey);
          if (descriptor) break;
          object = Object.getPrototypeOf(object);
        }
        if (descriptor && typeof descriptor.set === 'function') {
          descriptor.set.call(actualReceiver, value);
          return true;
        }
        if (descriptor && !Object.prototype.hasOwnProperty.call(descriptor, 'value')) return false;
        if (descriptor && descriptor.writable === false) return false;
        receiverDescriptor = Object.getOwnPropertyDescriptor(actualReceiver, propertyKey);
        if (receiverDescriptor && !Object.prototype.hasOwnProperty.call(receiverDescriptor, 'value')) return false;
        if (receiverDescriptor && receiverDescriptor.writable === false) return false;
        Object.defineProperty(actualReceiver, propertyKey, {
          value: value,
          writable: true,
          enumerable: receiverDescriptor ? !!receiverDescriptor.enumerable : true,
          configurable: receiverDescriptor ? !!receiverDescriptor.configurable : true
        });
        return true;
      } catch (error) {
        return false;
      }
    };
  }
  if (typeof root.Reflect.deleteProperty !== 'function') {
    root.Reflect.deleteProperty = function(target, propertyKey) {
      try {
        return delete target[propertyKey];
      } catch (error) {
        return false;
      }
    };
  }
  if (typeof root.Reflect.has !== 'function') {
    root.Reflect.has = function(target, propertyKey) {
      try {
        return propertyKey in Object(target);
      } catch (error) {
        return false;
      }
    };
  }
  if (typeof root.Reflect.ownKeys !== 'function') {
    root.Reflect.ownKeys = function(target) {
      var keys = Object.getOwnPropertyNames(target);
      var symbols = typeof Object.getOwnPropertySymbols === 'function'
        ? Object.getOwnPropertySymbols(target)
        : [];
      return keys.concat(symbols);
    };
  }
  if (typeof root.Reflect.defineProperty !== 'function') {
    root.Reflect.defineProperty = function(target, propertyKey, attributes) {
      try {
        Object.defineProperty(target, propertyKey, attributes);
        return true;
      } catch (error) {
        return false;
      }
    };
  }
  if (typeof root.Reflect.getPrototypeOf !== 'function') {
    root.Reflect.getPrototypeOf = function(target) {
      return Object.getPrototypeOf(target);
    };
  }
  if (typeof root.Reflect.construct !== 'function') {
    root.Reflect.construct = function(target, argumentsList, newTarget) {
      var prototypeSource = newTarget || target;
      var prototype = prototypeSource && prototypeSource.prototype;
      var instance = Object.create(prototype && typeof prototype === 'object' ? prototype : Object.prototype);
      var result = target.apply(instance, argumentsList || []);
      return isObject(result) ? result : instance;
    };
  }

  // The Rhino-provided Proxy handles plain object properties but misses the
  // indexed/length trap sequence used by mutating Array methods. Route every
  // document through AUI's generic host Proxy so Vue can observe push/unshift/
  // splice without component-specific workarounds.
  if (typeof root.Proxy === 'undefined' || !root.Proxy.__auiHostProxy) {
    var ProxyPolyfill = function(target, handler) {
      if (!host || typeof host.createProxy !== 'function') {
        throw new TypeError('Proxy is not supported by this host');
      }
      return host.createProxy(target, handler);
    };
    if (typeof host.createRevocableProxy === 'function') {
      ProxyPolyfill.revocable = function(target, handler) {
        return host.createRevocableProxy(target, handler);
      };
    }
    ProxyPolyfill.__auiHostProxy = true;
    root.Proxy = ProxyPolyfill;
  }
})();
