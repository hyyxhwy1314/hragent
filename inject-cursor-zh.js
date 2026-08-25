const fs = require('fs');
const path = require('path');
const crypto = require('crypto');

const TOOL_DIR = 'C:\\Users\\Administrator\\Desktop\\hragent\\cursor-zh-tool\\cursor-zh';
const LOCALIZATION_DIR = path.join(TOOL_DIR, 'localization');
const RUNTIME_DIR = path.join(LOCALIZATION_DIR, 'runtime');

const CURSOR_INSTALL_DIR = 'D:\\cursor';
const APP_DIR = path.join(CURSOR_INSTALL_DIR, 'resources', 'app');
const WORKBENCH_DIR = path.join(APP_DIR, 'out', 'vs', 'code', 'electron-sandbox', 'workbench');
const WORKBENCH_HTML = path.join(WORKBENCH_DIR, 'workbench.html');
const PRODUCT_JSON = path.join(APP_DIR, 'product.json');
const OUTPUT_JS = path.join(WORKBENCH_DIR, 'Cursor_Localization.js');

// --- Helper functions ---
function readRuntime(name) {
    return fs.readFileSync(path.join(RUNTIME_DIR, name), 'utf8');
}

function readJson(name) {
    return JSON.parse(fs.readFileSync(path.join(LOCALIZATION_DIR, name), 'utf8'));
}

function readCoreDict() {
    return readJson('Core_Dictionary.json');
}

function readPatternDict() {
    return readJson('Pattern_Dictionary.json');
}

function readAdPopupDict() {
    return readJson('Ad_Popup_Dictionary.json');
}

function readPluginMarketDict() {
    return readJson('Plugin_Marketplace_Dictionary.json');
}

function readFragmentEntries(fileName, entriesKey = 'entries') {
    try {
        const data = readJson(fileName);
        const entries = data[entriesKey] || [];
        return entries.filter(e => Array.isArray(e) && e.length >= 2 && e[0] && e[1]).map(e => [String(e[0]), String(e[1])]);
    } catch { return []; }
}

function readCursorSettingsFragments() {
    try { return readJson('Cursor_Settings_Fragments.json'); } catch { return {}; }
}

// --- Build JS blocks ---
function genFanYiCiDian(dict) {
    const json = JSON.stringify(dict);
    return `    var FanYi_CiDian = new Map(${json});`;
}

function genMoShiFanYi(patterns) {
    if (!patterns || !patterns.length) return '    var MoShi_FanYi = [];';
    const lines = patterns.map(p => {
        const re = JSON.stringify(p.regex || '');
        const flags = JSON.stringify(p.flags || '');
        const repl = JSON.stringify(p.replacement || '');
        return `        [new RegExp(${re}, ${flags}), ${repl}]`;
    });
    return '    var MoShi_FanYi = [\n' + lines.join(',\n') + '\n    ];';
}

function genGuangGaoHeBing() {
    try {
        const dict = readAdPopupDict();
        const lines = [];
        if (dict.entries) {
            for (const [en, zh] of dict.entries) {
                if (en && zh) lines.push(`        [${JSON.stringify(en)}, ${JSON.stringify(zh)}],`);
            }
        }
        if (!lines.length) return '';
        return '    var GuangGao_TanChuang_CiDian = [\n' + lines.join('\n') + '\n    ];';
    } catch { return ''; }
}

function genFragmentArray(varName, entries) {
    if (!entries || !entries.length) return `    var ${varName} = [];`;
    const lines = entries.map(([en, zh]) => `        [${JSON.stringify(en)}, ${JSON.stringify(zh)}],`);
    return `    var ${varName} = [\n${lines.join('\n')}\n    ];`;
}

function genCursorSettingsFragments() {
    const data = readCursorSettingsFragments();
    const symlink = data.symlink || {};
    const parts = [
        `    var Cursor_SheZhi_Symlink_Zh = ${JSON.stringify(String(symlink.zh || ''))};`,
        `    var Cursor_SheZhi_Symlink_ZhAdmin = ${JSON.stringify(String(symlink.zhAdmin || ''))};`,
        `    var Cursor_SheZhi_Symlink_Tail = ${JSON.stringify(String(symlink.tail || ''))};`,
        genFragmentArray('Cursor_SheZhi_MCP_SuiPian', readFragmentEntries('Cursor_Settings_Fragments.json', 'mcpEntries')),
        genFragmentArray('Cursor_SheZhi_Domain_SuiPian', readFragmentEntries('Cursor_Settings_Fragments.json', 'domainEntries')),
    ];
    return parts.join('\n');
}

function genPluginMarketBlock() {
    try {
        const dict = readPluginMarketDict();
        const parts = [];
        if (dict.categories) {
            parts.push(`    var ChaJian_FenLei = new Map(${JSON.stringify(dict.categories)});`);
        }
        if (dict.entries) {
            const lines = dict.entries.filter(([en, zh]) => en && zh).map(([en, zh]) => `        [${JSON.stringify(en)}, ${JSON.stringify(zh)}],`);
            if (lines.length) {
                parts.push('    var ChaJian_ShiChang_CiDian = [\n' + lines.join('\n') + '\n    ];');
            }
        }
        return parts.join('\n');
    } catch { return ''; }
}

// --- Insert helpers into engine.js ---
function insertKeywords(engineJs) {
    const marker = '    function HuoQu_QuanJu_WenBen()';
    const keywords = readRuntime('keywords.js').trimEnd() + '\n\n';
    if (!engineJs.includes(marker)) return engineJs;
    if (engineJs.includes('var QuanJu_GuanJianCi_Biao')) return engineJs;
    const idx = engineJs.indexOf(marker);
    return engineJs.slice(0, idx) + keywords + engineJs.slice(idx);
}

function insertHelpers(engineJs) {
    const marker = '    function XiuZheng_DaiMaKu_ShuoMing()';
    const helpers = readRuntime('helpers.js').trimEnd() + '\n\n';
    if (!engineJs.includes(marker)) {
        return engineJs.trimEnd() + '\n\n' + helpers;
    }
    const idx = engineJs.indexOf(marker);
    return engineJs.slice(0, idx) + helpers + engineJs.slice(idx);
}

// --- Compute checksum (same as VS Code) ---
function computeChecksum(filePath) {
    const content = fs.readFileSync(filePath);
    const hash = crypto.createHash('md5').update(content).digest('hex');
    return Buffer.from(hash).toString('base64').replace(/=+$/, '');
}

// --- Main build ---
function build() {
    const parts = [];

    // 1. Bootstrap
    let bootstrap = readRuntime('bootstrap.js');
    bootstrap = bootstrap.replace('__BUILD_TIMESTAMP__', new Date().toISOString().replace('T', ' ').substring(0, 19));
    parts.push(bootstrap.trimEnd());

    // 2. Core dictionary
    const coreDict = readCoreDict();
    parts.push(genFanYiCiDian(coreDict).trimEnd());

    // 3. Ad popup dictionary
    const adBlock = genGuangGaoHeBing();
    if (adBlock) parts.push(adBlock.trimEnd());

    // 4. Pattern dictionary
    const patternDict = readPatternDict();
    parts.push(genMoShiFanYi(patternDict).trimEnd());

    // 5. Engine with replacements
    let engine = readRuntime('engine.js');
    engine = engine.replace('    // __PARTIAL_FRAGMENTS_BLOCK__', genFragmentArray('DingXiang_SuiPian', readFragmentEntries('Partial_Fragments.json')));
    engine = engine.replace('    // __DROPDOWN_FRAGMENTS_BLOCK__', genFragmentArray('XiaLa_MianBan_SuiPian', readFragmentEntries('Dropdown_Fragments.json')));
    engine = engine.replace('    // __CURSOR_SETTINGS_FRAGMENTS_BLOCK__', genCursorSettingsFragments());
    engine = insertKeywords(engine);
    engine = insertHelpers(engine);
    parts.push(engine.trimEnd());

    // 6. Market
    let market = readRuntime('market.js');
    const marketBlock = genPluginMarketBlock();
    if (marketBlock) {
        market = market.replace('    // __PLUGIN_MARKETPLACE_BLOCK__', marketBlock.trimEnd());
    }
    parts.push(market.trimEnd());

    // 7. Init
    parts.push(readRuntime('init.js').trimEnd());

    return parts.join('\n\n') + '\n';
}

// --- Inject into workbench.html ---
function injectHtml() {
    const html = fs.readFileSync(WORKBENCH_HTML, 'utf8');
    const marker = '<!-- CURSOR_LOCALIZATION_INJECTION -->';
    const injectCode = `\n\t${marker}\n\t<script src="./Cursor_Localization.js"></script>\n`;

    let newHtml;
    if (html.includes('</body>')) {
        newHtml = html.replace('</body>', `</body>\n${injectCode}`);
    } else {
        newHtml = html.replace('</html>', `${injectCode}\n</html>`);
    }

    // Write without BOM
    fs.writeFileSync(WORKBENCH_HTML, newHtml, 'utf8');
    console.log('[注入] workbench.html 已注入脚本引用');
}

// --- Update product.json checksums ---
function updateChecksums() {
    const productText = fs.readFileSync(PRODUCT_JSON, 'utf8');
    const product = JSON.parse(productText);
    const checksums = product.checksums;
    if (!checksums || typeof checksums !== 'object') {
        console.log('[警告] product.json 中没有 checksums');
        return;
    }

    let updated = 0;
    for (const key of Object.keys(checksums)) {
        // Map key to file path
        const rel = key.replace(/\\/g, '/').replace(/^\//, '');
        const candidate1 = path.join(APP_DIR, 'out', rel.replace(/\//g, path.sep));
        const candidate2 = path.join(APP_DIR, rel.replace(/\//g, path.sep));
        let filePath = fs.existsSync(candidate1) ? candidate1 : (fs.existsSync(candidate2) ? candidate2 : null);

        if (!filePath) {
            continue;
        }

        const newHash = computeChecksum(filePath);
        if (checksums[key] !== newHash) {
            checksums[key] = newHash;
            updated++;
        }
    }

    // Write back
    fs.writeFileSync(PRODUCT_JSON, JSON.stringify(product, null, '\t') + '\n', 'utf8');
    console.log(`[校验] 已更新 product.json 中 ${updated} 项 checksum`);
}

// --- Run ---
try {
    console.log('=== Cursor 汉化注入工具 (Node.js 版) ===\n');

    // Step 1: Build JS
    console.log('[构建] 正在生成汉化脚本...');
    const jsContent = build();
    fs.writeFileSync(OUTPUT_JS, jsContent, 'utf8');
    console.log(`[写入] ${OUTPUT_JS} (${Buffer.byteLength(jsContent)} bytes)`);

    // Step 2: Backup and inject HTML (backup to workspace dir to avoid sandbox)
    const backupDir = 'C:\\Users\\Administrator\\Desktop\\hragent\\cursor-backup';
    if (!fs.existsSync(backupDir)) fs.mkdirSync(backupDir, { recursive: true });
    const backupPath = path.join(backupDir, 'workbench.html.bak');
    const productBackup = path.join(backupDir, 'product.json.bak');
    try {
        if (!fs.existsSync(backupPath)) {
            fs.copyFileSync(WORKBENCH_HTML, backupPath);
            console.log(`[备份] workbench.html 已备份到 ${backupPath}`);
        }
        if (!fs.existsSync(productBackup)) {
            fs.copyFileSync(PRODUCT_JSON, productBackup);
            console.log(`[备份] product.json 已备份到 ${productBackup}`);
        }
    } catch (e) {
        console.log(`[警告] 备份失败: ${e.message}，继续执行...`);
    }

    // Check if already injected
    const currentHtml = fs.readFileSync(WORKBENCH_HTML, 'utf8');
    if (currentHtml.includes('CURSOR_LOCALIZATION_INJECTION') || currentHtml.includes('CURSOR_HANHUA_INJECTION')) {
        console.log('[跳过] workbench.html 已注入，仅更新脚本文件');
    } else {
        injectHtml();
    }

    // Step 3: Update checksums
    updateChecksums();

    console.log('\n=== 汉化完成！请完全退出并重启 Cursor ===');
} catch (err) {
    console.error('[错误]', err.message);
    console.error(err.stack);
    process.exit(1);
}
