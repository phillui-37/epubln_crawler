const render = ((pubSub) => {
    const fieldMap = {
        "名字": "name",
        "圖片連接": "img_link",
        "檔案連接": "dl_link",
    };
    const isDesc = pubSub.register("isDesc", true);
    const isAll = pubSub.register("isAll", false);
    const arrow = () => isDesc.get()?"&#8595;":"&#8593;";

    const createTable = params => {
        let ret = '<table><thead><tr>';
        ret += '<th>全選<input type="checkbox" onchange="indexFn.checkAll()"></th>';
        for (const i in params.head) {
            ret += `<th><div onclick='indexFn.sort("${fieldMap[params.head[i]]}")'>
            ${params.head[i]}${(field === fieldMap[params.head[i]]) ? " " +arrow() : ""}
            </div></th>`;
        }
        ret += '</tr></thead><tbody>';

        for (const i in params.body) {
            ret += '<tr>';
            for (const j in params.body[i]) {
                ret += `<td>${params.body[i][j]}</td>`;
            }
            ret += '</tr>';
        }
        ret += '</tbody></table>';
        return ret;
    };
    const table = async () => {
        const html = {
            head: Object.keys(fieldMap),
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
    const top = async () => {
        let html = `<div class="search-box"><input id="search" value="${search}" type="text" placeholder="搜尋" oninput="indexFn.setSearch(this.value)"><button type="button" onclick="indexFn.search();">搜尋</button></div>`
        + '<div class="multi-download"><button type="button" onclick="indexFn.invokeServerDownload()">多項下載</button><button type="button" onclick="indexFn.customDownload()">自選目錄下載</button></div>'
        + '<div class="page-nav"><button type="button" onclick="indexFn.prevPage()">上一頁</button>'
        + '<select id="page" onchange="indexFn.choosePage(this.value)">';
        const maxPage = Math.ceil(totalRecordSize / limit);
        for (let i = 1; i <= maxPage; i++) {
            html += `<option value="${i}">${i}</option>`;
        }
        html += '</select><button type="button" onclick="indexFn.nextPage()">下一頁</button></div>';
        document.querySelector('div.top').innerHTML = html;
    };
    const clearTop = () => {
        document.querySelector("div.top").innerHTML = "";
    };
    return {
        createTable,
        table,
        top,
        clearTop
    };
})(PostOffice);