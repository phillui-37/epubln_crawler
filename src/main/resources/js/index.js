var isAll = false;

const indexFn = (() => {
    let offset = 0;
    let limit = 20;
    let isDesc = false;
    let field = "name";
    let totalRecordSize = 0;
    let port = 0;
    let search = "";
    let task;
    const createTable = params => {
        let ret = '<table><thead><tr>';
        ret += '<th>全選<input type="checkbox" onchange="indexFn.checkAll()"></th>'
        for (const i in params.head) {
            ret += `<th>${params.head[i]}</th>`;
        }
        ret += '</tr></thead><tbody>'

        for (const i in params.body) {
            ret += '<tr><td><input type="checkbox" class="book-option"></td>'
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
        let html = `<div class="search-box"><input id="search" value="${search}" type="text" placeholder="搜尋" oninput="indexFn.search(this.value)"></div>`;
        html += '<div class="page-nav"><button type="button" onclick="indexFn.prevPage()">上一頁</button><select onchange="indexFn.choosePage(this.value)">';
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
                        `<a href="${ls[i].page_link}">${ls[i].name}</a>`,
                        `<a href="${ls[i].img_link}"><img src="${ls[i].img_link}"></a>`,
                        `<a href="${ls[i].dl_link}">下載</a>`
                    ])
                }
            })
            .catch(console.error);
        document.querySelector("div.display-area").innerHTML = createTable(html);
    };
    return {
        init: async (p) => {
            port = p;
            await getTotalRecordSize();
            await renderTop();
            await renderTable();
        },
        checkAll: () => {
            const target = document.getElementsByClassName("book-option");
            for (let i = 0; i < target.length; i++) {
                target[i].checked = !target[i].checked;
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
        search: word => {
            if (task !== undefined) {
                clearTimeout(task);
                task = undefined;
            }
            task = setTimeout(async () => {
                search = word;
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
            }, 300);
        },
        download: dlLinks => {
            fetch(`http://localhost:${port}/download`, {
                    method: 'POST',
                    headers: {
                        'Content-Type': 'application/json'
                    },
                    body: JSON.stringify({
                        dlLinks
                    })
                })
                .then(res => res.json())
                .then(map => {
                    let html;
                    if (Object.keys(map).length !== 0) {
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
        }
    };
})();