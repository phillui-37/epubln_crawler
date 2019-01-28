const Postman = (id, v) => {
    let subMap = {};
    let key_ = id;
    let value_ = v;
    const changeWithoutPublish = value => {value_ = value};
    const change = (key = undefined) => value => {
        value_ = value;
        for (let k of Object.keys(subMap).filter(x => x !== key)) {
            subMap[k](value);
        }
    };
    const subscribe = (id, cb) => subMap[id] = cb;
    const unsubscribe = id => {
        delete subMap[id];
    };
    const get = () => value_;
    const getKey = () => key_;
    return {
        change,
        changeWithoutPublish,
        subscribe,
        unsubscribe,
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
            delete postmanMap[channel];
        }
    };
    const register = /** @type Postman */ (channel, value) => {
        if (!keyExists(channel)) {
            postmanMap[channel] = Postman(channel, value);
        }
        return postmanMap[channel];
    };
    return {
        register,
        postmanList,
        unregister
    };
})();