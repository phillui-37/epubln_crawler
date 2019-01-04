const indexFn = (() => {
    let offset = 0;
    let limit = 20;
    let isDesc = false;
    let field = "name";
    let totalRecordSize = 0;
    let port = 0;
    let search = "";
    let isAll = false;
    let downloadQueue = [];
    const createTable = params => {
        let ret = '<table><thead><tr>';
        ret += '<th>全選<input type="checkbox" onchange="indexFn.checkAll()"></th>'
        for (const i in params.head) {
            ret += `<th>${params.head[i]}</th>`;
        }
        ret += '</tr></thead><tbody>'

        for (const i in params.body) {
            ret += '<tr>'
            for (const j in params.body[i]) {
                ret += `<td>${params.body[i][j]}</td>`
            }
            ret += '</tr>'
        }
        ret += '</tbody></table>'
        return ret;
    };
    const getTotalRecordSize = async () => await fetch(
            `http://localhost:${port}/count`, {
                method: "POST",
                headers: {
                    "Content-Type": "application/json"
                },
                body: JSON.stringify({
                    related_names: search
                })
            })
        .then(res => res.json())
        .then(c => {
            totalRecordSize = c;
        })
        .catch(console.error);
    const renderTop = async () => {
        let html = `<div class="search-box"><input id="search" value="${search}" type="text" placeholder="搜尋" oninput="indexFn.setSearch(this.value)"><button type="button" onclick="indexFn.search();">搜尋</button></div>`;
        html += '<div class="multi-download"><button type="button" onclick="indexFn.invokeServerDownload()">多項下載</button><button type="button" onclick="indexFn.customDownload()">自選目錄下載</button></div>';
        html += '<div class="page-nav"><button type="button" onclick="indexFn.prevPage()">上一頁</button>';
        html += '<select onchange="indexFn.choosePage(this.value)">';
        const maxPage = Math.ceil(totalRecordSize / limit);
        for (let i = 1; i <= maxPage; i++) {
            html += `<option value="${i}">${i}</option>`;
        }
        html += '</select><button type="button" onclick="indexFn.nextPage()">下一頁</button></div>';
        document.querySelector('div.top').innerHTML = html;
    };
    const renderTable = async () => {
        const html = {
            head: ["名字", "圖片連接", "檔案連接"],
            body: []
        };
        await fetch(`http://localhost:${port}/raw`, {
                method: "POST",
                headers: {
                    "Content-Type": "application/json"
                },
                body: JSON.stringify({
                    limit,
                    offset,
                    isDesc,
                    field,
                    search,
                    isAll
                })
            })
            .then(res => res.json())
            .then(ls => {
                for (const i in ls) {
                    html.body.push([
                        `<input type="checkbox" class="book-option" onchange="indexFn.select('${ls[i].dl_link}')">`,
                        `<a href="${ls[i].page_link}">${ls[i].name}</a>`,
                        `<a href="${ls[i].img_link}"><img src="${ls[i].img_link}"></a>`,
                        `<button type="button" onclick="indexFn.download('${ls[i].dl_link}')">下載</button>`
                    ])
                }
            })
            .catch(console.error);
        document.querySelector("div.display-area").innerHTML = createTable(html);
    };
    const clearTop = () => {
        document.querySelector("div.top").innerHTML = "";
    };
    return {
        init: async (p = port) => {
            port = p;
            await getTotalRecordSize();
            await renderTop();
            await renderTable();
        },
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
                renderTable();
            }
        },
        prevPage: async () => {
            if (offset >= limit) {
                offset -= limit;
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
            fetch(`http://localhost:${port}/api/init_server`, {
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