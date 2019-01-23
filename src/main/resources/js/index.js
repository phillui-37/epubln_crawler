const indexFn = (() => {
    const offset = PostOffice.register("offset", 0);
    const limit = PostOffice.register("limit", 20);
    const isDesc = PostOffice.register("isDesc", true);
    const field = PostOffice.register("field", "name");
    const isAll = PostOffice.register("isAll", false);
    const search = PostOffice.register("search", "");
    const totalRecordSize = PostOffice.register("totalRecordSize", 0);
    let port = 0;
    let downloadQueue = [];

    const getTotalRecordSize = async () => await fetch(
        `http://localhost:${port}/count`, {
            method: "POST",
            headers: {
                "Content-Type": "application/json"
            },
            body: JSON.stringify({
                related_names: search.get()
            })
        })
        .then(res => res.json())
        .then(totalRecordSize.change())
        .catch(console.error);
    const init = async (p = port) => {
        port = p;
        await getTotalRecordSize();
        await render.top();
        await render.table();
    };
    const sort = newField => {
        if (newField === field) {
            isDesc.change();
        } else {
            field = newField;
            isDesc = true;
        }
        indexFn.init();
    };
    return {
        init,
        sort,
        nonHandledBookList: () => {
            isAll = false;
            indexFn.init();
        },
        allBookList: () => {
            isAll = true;
            indexFn.init();
        },
        checkAll: () => {
            const target = document.getElementsByClassName("book-option");
            for (let i = 0; i < target.length; i++) {
                target[i].checked = !target[i].checked;
                target[i].onchange();
            }
        },
        nextPage: () => {
            if (offset < totalRecordSize - limit) {
                offset += limit;
                document.querySelector("select#page").value = (offset / limit) + 1;
                renderTable();
            }
        },
        prevPage: async () => {
            if (offset >= limit) {
                offset -= limit;
                document.querySelector("select#page").value = (offset / limit) + 1;
                renderTable();
            }
        },
        choosePage: pageNo => {
            offset = (pageNo - 1) * limit;
            renderTable();
        },
        changeLimit: newLimit => {
            limit = newLimit;
            indexFn.init(port);
        },
        setSearch: word => {
            search = word;
        },
        search: async () => {
            offset = 0;
            await indexFn.init(port);
            const element = document.getElementById('search');
            if (element.createTextRange) {
                const range = element.createTextRange();
                range.move('character', search.length);
                range.select();
            } else if (element.setSelectionRange) {
                element.focus();
                element.setSelectionRange(search.length, search.length);
            }
            task = undefined;
        },
        select: dlLink => {
            if (downloadQueue.includes(dlLink)) {
                downloadQueue = downloadQueue.filter(x => x != dlLink);
            } else {
                downloadQueue.push(dlLink);
            }
        },
        customDownload: async () => {
            const defaultDir = await fetch(`http://localhost:${port}/api/download_dir`)
                .then(res => res.text())
                .catch(console.error);
            document.querySelector('div.display-area').innerHTML = `<input id="download-custom-dir" value="${defaultDir}" type="text" placeholder="下載"><button type="button" onclick="indexFn.invokeServerDownload(document.querySelector('input#download-custom-dir').value)">下載</button>`;
        },
        download: dlLink => {
            downloadQueue.push(dlLink);
            indexFn.invokeServerDownload()
        },
        invokeServerDownload: async (dir = "") => {
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
        },
        fetchResource: () => {
            clearTop();
            fetch(`http://localhost:${port}/api/update_db`, {
                method: 'POST'
            })
                .then(_ => {
                    document.querySelector('div.display-area').innerHTML = "已開始更新資源列表,5秒後將回到未處理列表"
                    setTimeout(indexFn.init, 5000);
                })
                .catch(console.error);
        },
        renderUpdateConfPage: () => {
            fetch(`http://localhost:${port}/api/conf`)
                .then(res => res.json())
                .then(data => {
                    clearTop();
                    let html = [
                        `下載預設目錄: <input id="conf-dir" value="${data.dir}" type="text">`,
                        `GeckoDriver位置: <input id="conf-gecko" value="${data["geckodriver_location"]}" type="text">`,
                        `端口位置: <input id="conf-port" value="${data.port}" type="number" min="1024" max="65535">`,
                        `<button type="button" onclick="indexFn.updateConf()">確定</button>`
                    ].join("<br/>");
                    document.querySelector("div.display-area").innerHTML = html;
                });
        },
        updateConf: async () => {
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
                    setTimeout(indexFn.stop, 5000);
                })
                .catch(console.error);
        },
        stop: async () => {
            await fetch(`http://localhost:${port}/api/stop`, {
                method: 'POST'
            })
        }
    };
})();