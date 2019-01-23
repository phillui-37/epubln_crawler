const Postman = (id, v) => {
    let subMap = {};
    let key_ = id;
    let value_ = v;
    const change = (key = undefined, src = undefined) => value => {
        value_ = value;
        if (!(src instanceof Postman)) {
            for (let k of Object.keys(subMap).filter(x => x !== key)) {
                subMap[k](value);
            }
        }
    };
    const listen = (id, cb) => subMap[id] = cb;
    const cancelListen = id => {
        delete subMap[id];
    };
    const get = () => value_;
    const getKey = () => key_;
    return {
        change,
        listen,
        cancelListen,
        get,
        getKey
    };
};

const PostOffice = (() => {
    let postmanMap = {};
    const keyExists = key => postmanMap[key] !== undefined;
    const postmanList = () => Object.keys(postmanMap);
    const unregister = channel => {
        if (keyExists(channel)) {
            delete subMap[channel];
        }
    };
    const register = /** @type Postman */ (channel, value) => {
        if (!keyExists(channel)) {
            subMap[channel] = Postman(channel, value);
        }
        return subMap[channel];
    };
    return {
        register,
        postmanList,
        unregister
    };
})();