(function() {
    function point(svg, x, y) {
        var p = svg.createSVGPoint();
        p.x = x;
        p.y = y;
        return p.matrixTransform(svg.getScreenCTM().inverse());
    }

    function node(svg, id) {
        return Array.from(svg.querySelectorAll('.viz-node')).find(function(n) {
            return n.dataset.nodeId === id;
        });
    }

    function nodeBox(svg, id) {
        var n = node(svg, id);
        if (!n) return null;
        var face = n.querySelector('.info-face, .rule-face'),
            b = (face || n).getBBox(),
            dx = parseFloat(n.dataset.dx || 0),
            dy = parseFloat(n.dataset.dy || 0);
        return {
            x: b.x + dx,
            y: b.y + dy,
            w: b.width,
            h: b.height,
            cx: b.x + dx + b.width / 2,
            cy: b.y + dy + b.height / 2
        };
    }

    function endpoints(a, b) {
        var dx = b.cx - a.cx,
            dy = b.cy - a.cy;
        if (Math.abs(dx) >= Math.abs(dy)) {
            var s = dx >= 0 ? 1 : -1;
            return [a.cx + s * a.w / 2, a.cy, b.cx - s * b.w / 2, b.cy];
        }
        var s2 = dy >= 0 ? 1 : -1;
        return [a.cx, a.cy + s2 * a.h / 2, b.cx, b.cy - s2 * b.h / 2];
    }

    function updateEdge(svg, g) {
        if (g.style.display === 'none') return;
        var a = nodeBox(svg, g.dataset.source),
            b = nodeBox(svg, g.dataset.target);
        if (!a || !b) return;
        var e = endpoints(a, b),
            x1 = e[0],
            y1 = e[1],
            x2 = e[2],
            y2 = e[3],
            bend = parseFloat(g.dataset.bend || 0),
            path = g.querySelector('.edge-path'),
            label = g.querySelector('.edge-label'),
            mx = (x1 + x2) / 2,
            my = (y1 + y2) / 2,
            d, lx, ly;
        if (bend) {
            my += bend;
            d = 'M ' + x1 + ' ' + y1 + ' Q ' + mx + ' ' + my + ', ' + x2 + ' ' + y2;
            lx = mx;
            ly = my - 5;
        } else {
            d = 'M ' + x1 + ' ' + y1 + ' C ' + mx + ' ' + y1 + ', ' + mx + ' ' + y2 + ', ' + x2 + ' ' + y2;
            lx = mx;
            ly = (y1 + y2) / 2 - 6;
        }
        path.setAttribute('d', d);
        if (label) {
            label.setAttribute('x', lx);
            label.setAttribute('y', ly);
        }
    }

    function updateEdges(svg, nodeId) {
        svg.querySelectorAll('.graph-edge').forEach(function(g) {
            if (!nodeId || g.dataset.source === nodeId || g.dataset.target === nodeId) updateEdge(svg, g);
        });
    }

    function zoom(svg, factor, cx, cy) {
        var vb = svg.viewBox.baseVal,
            pt = point(svg, cx, cy),
            nw = vb.width * factor,
            nh = vb.height * factor,
            rx = (pt.x - vb.x) / vb.width,
            ry = (pt.y - vb.y) / vb.height;
        vb.x = pt.x - rx * nw;
        vb.y = pt.y - ry * nh;
        vb.width = nw;
        vb.height = nh;
    }

    function reset(svg) {
        var v = (svg.dataset.originalViewbox || '0 0 1000 700').split(/\s+/).map(Number),
            vb = svg.viewBox.baseVal;
        vb.x = v[0];
        vb.y = v[1];
        vb.width = v[2];
        vb.height = v[3];
        svg.querySelectorAll('.viz-node').forEach(function(n) {
            n.dataset.dx = '0';
            n.dataset.dy = '0';
            n.removeAttribute('transform');
            delete n.dataset.expansionDirection;
            if (n.dataset.expansionNode) n.style.display = 'none';
        });
        svg.querySelectorAll('.rule-detail-node, .rule-detail-edge').forEach(function(n) {
            n.style.display = 'none';
        });
        svg.querySelectorAll('.info-expand-control').forEach(function(c) {
            c.setAttribute('aria-expanded', 'false');
            c.querySelector('text').textContent = '＋';
        });
        svg.querySelectorAll('.graph-edge').forEach(function(e) {
            if (e.dataset.expansionEdge) e.style.display = 'none';
        });
        updateEdges(svg);
    }

    function positionNode(n) {
        n.setAttribute('transform', 'translate(' + parseFloat(n.dataset.dx || 0) + ' ' + parseFloat(n.dataset.dy || 0) + ')');
    }

    function overlaps(a, b) {
        return a.x < b.x + b.w + 24 && a.x + a.w + 24 > b.x && a.y < b.y + b.h + 24 && a.y + a.h + 24 > b.y;
    }

    function placeRulePanel(svg, source, panel, direction) {
        var a = nodeBox(svg, source.dataset.nodeId),
            size = nodeBox(svg, panel.dataset.nodeId),
            b = {
                x: a.cx - size.w / 2,
                y: a.cy - size.h / 2,
                w: size.w,
                h: size.h
            };
        if (direction === 'up') b.y = a.y - b.h - 60;
        if (direction === 'down') b.y = a.y + a.h + 60;
        if (direction === 'left') b.x = a.x - b.w - 60;
        if (direction === 'right') b.x = a.x + a.w + 60;
        var obstacles = Array.from(svg.querySelectorAll('.viz-node')).filter(function(n) {
            return n !== panel && n !== source && n.style.display !== 'none';
        }).map(function(n) {
            return nodeBox(svg, n.dataset.nodeId);
        });
        for (var i = 0; i <= obstacles.length; i++) {
            var hit = obstacles.filter(function(o) {
                return overlaps(b, o);
            });
            if (!hit.length) break;
            if (direction === 'up') b.y = Math.min.apply(null, hit.map(function(o) {
                return o.y;
            })) - b.h - 40;
            else if (direction === 'down') b.y = Math.max.apply(null, hit.map(function(o) {
                return o.y + o.h;
            })) + 40;
            else if (direction === 'left') b.x = Math.min.apply(null, hit.map(function(o) {
                return o.x;
            })) - b.w - 40;
            else b.x = Math.max.apply(null, hit.map(function(o) {
                return o.x + o.w;
            })) + 40;
        }
        panel.dataset.dx = b.x;
        panel.dataset.dy = b.y;
        positionNode(panel);
    }

    function revealRulePair(svg, source, panel) {
        var a = nodeBox(svg, source.dataset.nodeId),
            b = nodeBox(svg, panel.dataset.nodeId),
            x = Math.min(a.x, b.x) - 30,
            y = Math.min(a.y, b.y) - 30,
            w = Math.max(a.x + a.w, b.x + b.w) - x + 30,
            h = Math.max(a.y + a.h, b.y + b.h) - y + 30,
            v = svg.viewBox.baseVal,
            f = Math.max(1, w / v.width, h / v.height);
        v.width *= f;
        v.height *= f;
        if (x < v.x) v.x = x;
        if (y < v.y) v.y = y;
        if (x + w > v.x + v.width) v.x = x + w - v.width;
        if (y + h > v.y + v.height) v.y = y + h - v.height;
    }

    function syncExpansion(svg) {
        var nodes = Array.from(svg.querySelectorAll('.viz-node')),
            visible = new Set(),
            queue = nodes.filter(function(n) {
                return n.dataset.expansionRoot;
            });
        while (queue.length) {
            var n = queue.shift(),
                id = n.dataset.nodeId;
            if (visible.has(id)) continue;
            visible.add(id);
            if (n.dataset.expansionDirection) JSON.parse(n.dataset.expansionTargets || '[]').forEach(function(id) {
                var target = node(svg, id);
                if (target) queue.push(target);
            });
        }
        nodes.forEach(function(n) {
            if (n.dataset.expansionNode) {
                n.style.display = visible.has(n.dataset.nodeId) ? '' : 'none';
                if (!visible.has(n.dataset.nodeId)) delete n.dataset.expansionDirection;
            }
        });
        svg.querySelectorAll('.info-expand-control').forEach(function(c) {
            if (c.dataset.expansion) {
                var active = c.closest('.viz-node').dataset.expansionDirection === c.dataset.direction;
                c.setAttribute('aria-expanded', String(active));
                c.querySelector('text').textContent = active ? '−' : '＋';
            }
        });
        svg.querySelectorAll('.graph-edge').forEach(function(e) {
            if (e.dataset.expansionEdge) {
                var source = node(svg, e.dataset.source);
                e.style.display = visible.has(e.dataset.source) && source.dataset.expansionDirection && visible.has(e.dataset.target) ? '' : 'none';
            }
        });
    }

    function toggleInformation(source, direction) {
        var svg = source.closest('.interactive-svg');
        if (source.dataset.expansionDirection === direction) delete source.dataset.expansionDirection;
        else source.dataset.expansionDirection = direction;
        syncExpansion(svg);
        if (source.dataset.expansionDirection) JSON.parse(source.dataset.expansionTargets).forEach(function(id) {
            var target = node(svg, id);
            placeRulePanel(svg, source, target, direction);
            revealRulePair(svg, source, target);
        });
        updateEdges(svg);
    }

    function selectNode(g) {
        activate(g);
        if (g.dataset.expansionTargets) toggleInformation(g, g.dataset.expansionDirection || 'right');
    }

    function toggleRulePanel(control) {
        if (control.dataset.expansion) {
            toggleInformation(control.closest('.viz-node'), control.dataset.direction);
            return;
        }
        var source = control.closest('.viz-node'),
            svg = source.closest('.interactive-svg'),
            panel = node(svg, control.dataset.panelId),
            expanded = control.getAttribute('aria-expanded') !== 'true';
        if (!panel) return;
        panel.style.display = expanded ? '' : 'none';
        control.setAttribute('aria-expanded', String(expanded));
        control.querySelector('text').textContent = expanded ? '−' : '＋';
        svg.querySelectorAll('.rule-detail-edge').forEach(function(edge) {
            if (edge.dataset.target === panel.dataset.nodeId) edge.style.display = expanded ? '' : 'none';
        });
        if (expanded) {
            placeRulePanel(svg, source, panel, control.dataset.direction);
            revealRulePair(svg, source, panel);
        }
        updateEdges(svg);
    }

    function activate(g) {
        var p = g.closest('.panel'),
            box = p && p.querySelector('.inspector-body');
        if (!box) return;
        var id = g.dataset.nodeId,
            tpl = Array.from(p.querySelectorAll('template.node-template')).find(function(t) {
                return t.dataset.detailId === id;
            });
        if (tpl) box.innerHTML = tpl.innerHTML;
    }
    document.querySelectorAll('.interactive-svg').forEach(function(svg) {
        updateEdges(svg);
        var pan = null,
            drag = null,
            moved = false;
        svg.addEventListener('wheel', function(e) {
            if (e.target.closest('.inline-information')) return;
            e.preventDefault();
            zoom(svg, e.deltaY > 0 ? 1.12 : .89, e.clientX, e.clientY);
        }, {
            passive: false
        });
        svg.addEventListener('pointerdown', function(e) {
            if (e.target.closest('.inline-information') || e.target.closest('.info-expand-control')) return;
            var n = e.target.closest('.viz-node');
            moved = false;
            if (n) {
                var p = point(svg, e.clientX, e.clientY);
                drag = {
                    node: n,
                    id: n.dataset.nodeId,
                    start: p,
                    dx: parseFloat(n.dataset.dx || 0),
                    dy: parseFloat(n.dataset.dy || 0)
                };
                n.setPointerCapture(e.pointerId);
                e.preventDefault();
                e.stopPropagation();
            } else {
                pan = {
                    x: e.clientX,
                    y: e.clientY,
                    vx: svg.viewBox.baseVal.x,
                    vy: svg.viewBox.baseVal.y
                };
                svg.setPointerCapture(e.pointerId);
            }
        });
        svg.addEventListener('pointermove', function(e) {
            if (drag) {
                var p = point(svg, e.clientX, e.clientY),
                    dx = drag.dx + p.x - drag.start.x,
                    dy = drag.dy + p.y - drag.start.y;
                if (Math.abs(dx - drag.dx) + Math.abs(dy - drag.dy) > 2) moved = true;
                drag.node.dataset.dx = dx;
                drag.node.dataset.dy = dy;
                positionNode(drag.node);
                updateEdges(svg, drag.id);
            } else if (pan) {
                var r = svg.getBoundingClientRect(),
                    vb = svg.viewBox.baseVal;
                vb.x = pan.vx - (e.clientX - pan.x) * vb.width / r.width;
                vb.y = pan.vy - (e.clientY - pan.y) * vb.height / r.height;
            }
        });
        svg.addEventListener('pointerup', function() {
            drag = null;
            pan = null;
        });
        svg.addEventListener('pointercancel', function() {
            drag = null;
            pan = null;
        });
        svg.querySelectorAll('.viz-node').forEach(function(g) {
            g.addEventListener('click', function(e) {
                var control = e.target.closest('.info-expand-control');
                if (control) {
                    toggleRulePanel(control);
                    return;
                }
                if (!moved && !e.target.closest('.inline-information')) selectNode(g);
            });
            g.addEventListener('keydown', function(e) {
                if (e.target.closest('.inline-information')) return;
                if (e.key === 'Enter' || e.key === ' ') {
                    e.preventDefault();
                    var control = e.target.closest('.info-expand-control');
                    if (control) toggleRulePanel(control);
                    else selectNode(g);
                }
            });
        });
    });
    document.querySelectorAll('[data-graph-action]').forEach(function(btn) {
        btn.addEventListener('click', function() {
            var panel = btn.closest('.panel'),
                svg = panel && panel.querySelector('.interactive-svg');
            if (!svg) return;
            var a = btn.dataset.graphAction,
                r = svg.getBoundingClientRect();
            if (a === 'in') zoom(svg, .82, r.left + r.width / 2, r.top + r.height / 2);
            else if (a === 'out') zoom(svg, 1.22, r.left + r.width / 2, r.top + r.height / 2);
            else if (a === 'reset') reset(svg);
        });
    });
})();
