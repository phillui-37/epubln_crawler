const PostOffice = (() => {
    let sub_map = {};
    const key_exists = key => sub_map[key] !== undefined;
    const channelList = () => Object.keys(sub_map);
    const subscribe = (channel, id, cb) => {
        if (key_exists(channel)) {
            sub_map[channel][id] = cb;
        }
    };
    const unsubscribe = (channel, id) => {
        if (key_exists(channel)) {
            delete sub_map[channel][id];
        }
    }
    const publish = (channel, value) => {
        if (key_exists(channel)) {
            for (let k of Object.keys(sub_map[channel])) {
                sub_map[channel][k](value);
            }
        }
    };
    const publishFromSubscriber = (channel, id, value) => {
        if (key_exists(channel)) {
            for (let k of Object.keys(sub_map[channel]).filter(x => x !== id)) {
                sub_map[channel][k](value);
            }
        }
    };
    const unregister = channel => {
        if (key_exists(channel)) {
            delete sub_map[channel];
        }
    };
    const register = channel => {
        if (!key_exists(channel)) {
            sub_map[channel] = {};
        }
    };
    const initRegister = (value, channel) => {
        register(channel);
        return value;
    };
    return {
        subscribe,
        publish,
        register,
        channelList,
        unregister,
        unsubscribe,
        publishFromSubscriber,
        initRegister,
    };
})()();