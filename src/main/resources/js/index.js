const indexFn = (() => {
    const totalRecordSize = PostOffice.register("totalRecordSize", 0);
    const offset = PostOffice.register("offset", 0);
    const limit = PostOffice.register("limit", 20);
    const isDesc = PostOffice.register("isDesc", true);
    const field = PostOffice.register("field", "name");
    const isAll = PostOffice.register("isAll", false);
    let searchCache = "";
    let downloadQueue = [];

    const getTotalRecordSize = async () => await fetch(
        `http://localhost:${port}/count`, {
            method: "POST",
            headers: {
                "Content-Type": "application/json"
            },
            body: JSON.stringify({
                related_names: searchCache,
                is_all: isAll.get()
            })
        })
        .then(res => res.json())
        .then(totalRecordSize.change())
        .catch(console.error);
    const registerCb = () => {
        totalRecordSize.subscribe('offset', (_) => offset.change('totalRecordSize')(0));
        limit.subscribe('offset', (_) => offset.change('limit')(0));
        isDesc.subscribe('offset', (_) => offset.change('isDesc')(0));
        field.subscribe('offset', (_) => offset.change('field')(0));
        isAll.subscribe('getAll', (_) => {renderAll()});
        offset.subscribe('render', (_) => {reRenderListOnly()});
    };
    const renderTable = async () => await render.table({ limit: limit.get(), offset: offset.get(), field: field.get(), search: searchCache, isAll: isAll.get() });
    const renderAll = async () => {
        await getTotalRecordSize();
        await render.top(totalRecordSize.get(), limit.get(), searchCache);
        await renderTable();
    };
    const reRenderListOnly = async () => {
        await renderTable();
    };
    const init = async () => {
        registerCb();
        await renderAll();
    };
    const sort = newField => {
        if (newField === field.get()) {
            isDesc.change()(!isDesc.get());
        } else {
            field.changeWithoutPublish(newField);
            isDesc.change('sort')(true);
        }
    };
    const nonHandledBookList = () => isAll.change()(false);
    const allBookList = () => isAll.change()(true);
    const checkAll = () => {
        const target = document.getElementsByClassName("book-option");
        for (let i = 0; i < target.length; i++) {
            target[i].checked = !target[i].checked;
            target[i].onchange();
        }
    };
    const nextPage = () => {
        if (offset.get() < totalRecordSize.get() - limit.get()) {
            offset.change('select#page')(offset.get() + limit.get());
            document.querySelector("select#page").value = (offset.get() / limit.get()) + 1;
        }
    };
    const prevPage = async () => {
        if (offset.get() >= limit.get()) {
            offset.change('select#page')(offset.get() - limit.get());
            document.querySelector("select#page").value = (offset.get() / limit.get()) + 1;
        }
    };
    const choosePage = pageNo => {
        offset.change('select#page')((pageNo - 1) * limit.get());
    };
    const changeLimit = newLimit => {
        limit.change('changeLimit')(newLimit);
    };
    const setSearch = word => {
        searchCache = word;
    };
    const search = async () => {
        await init();
        const element = document.getElementById('search');
        if (element.createTextRange) {
            const range = element.createTextRange();
            range.move('character', searchCache.length);
            range.select();
        } else if (element.setSelectionRange) {
            element.focus();
            element.setSelectionRange(searchCache.length, searchCache.length);
        }
    };
    const select = dlLink => {
        if (downloadQueue.includes(dlLink)) {
            downloadQueue = downloadQueue.filter(x => x != dlLink);
        } else {
            downloadQueue.push(dlLink);
        }
    };
    const customDownload = async () => {
        const defaultDir = await fetch(`http://localhost:${port}/api/download_dir`)
            .then(res => res.text())
            .catch(console.error);
        document.querySelector('div.display-area').innerHTML = `<input id="download-custom-dir" value="${defaultDir}" type="text" placeholder="下載"><button type="button" onclick="indexFn.invokeServerDownload(document.querySelector('input#download-custom-dir').value)">下載</button>`;
    };
    const download = dlLink => {
        downloadQueue.push(dlLink);
        invokeServerDownload()
    }
    const invokeServerDownload = async (dir = "") => {
        document.querySelector('div.display-area').innerHTML = '正在下載,請耐心等待';
        const target = [...downloadQueue];
        downloadQueue = [];
        await fetch(`http://localhost:${port}/download`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify({
                dlLinks: target,
                dir
            })
        })
            .then(res => res.json())
            .then(map => {
                let html;
                if (map !== "Success" && Object.keys(map).length !== 0) {
                    html = '以下書本無法下載,請點擊各書本名字查看詳情<br/><ul>';
                    for (const [k, v] of Object.entries(map)) {
                        html += `<li><a href="${v}">${k}</a></li>`
                    }
                    html += '</ul>';
                } else {
                    html = '<h1>所有書本已下載完畢</h1>'
                }
                document.querySelector('div.display-area').innerHTML = html;
            })
    };
    const fetchResource = () => {
        render.clearTop();
        fetch(`http://localhost:${port}/api/update_db`, {
            method: 'POST'
        })
            .then(_ => {
                document.querySelector('div.display-area').innerHTML = "已開始更新資源列表,5秒後將回到未處理列表"
                setTimeout(init, 5000);
            })
            .catch(console.error);
    };
    const renderUpdateConfPage = () => {
        fetch(`http://localhost:${port}/api/conf`)
            .then(res => res.json())
            .then(data => {
                render.clearTop();
                let html = [
                    `下載預設目錄: <input id="conf-dir" value="${data.dir}" type="text">`,
                    `GeckoDriver位置: <input id="conf-gecko" value="${data["geckodriver_location"]}" type="text">`,
                    `端口位置: <input id="conf-port" value="${data.port}" type="number" min="1024" max="65535">`,
                    `<button type="button" onclick="indexFn.updateConf()">確定</button>`
                ].join("<br/>");
                document.querySelector("div.display-area").innerHTML = html;
            });
    };
    const updateConf = async () => {
        await fetch(`http://localhost:${port}/api/change_conf`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify({
                dir: document.querySelector("input#conf-dir").value,
                geckodriver_location: document.querySelector("input#conf-gecko").value,
                'port': document.querySelector("input#conf-port").value
            })
        })
            .then(_ => {
                document.querySelector('div.content').innerHTML = '更新配置成功,服務器將於5秒後關閉,請重新開啟';
                setTimeout(stop, 5000);
            })
            .catch(console.error);
    };
    const stop = async () => {
        await fetch(`http://localhost:${port}/api/stop`, {
            method: 'POST'
        })
    };
    return {
        init,
        sort,
        nonHandledBookList,
        allBookList,
        checkAll,
        nextPage,
        prevPage,
        choosePage,
        changeLimit,
        setSearch,
        search,
        select,
        customDownload,
        download,
        invokeServerDownload,
        fetchResource,
        renderUpdateConfPage,
        updateConf,
        stop,
        renderAll,
        reRenderListOnly
    };
})();