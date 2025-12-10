// src/lib.rs

use wasm_bindgen::prelude::*;
use std::collections::HashMap;

const SCALE: f64 = 1e5;               // 위/경도 정수 스케일 (deg * 1e5)
const EARTH_RADIUS_M: f64 = 6_371_000.0;

#[derive(Clone, Copy, Debug)]
struct IntPoint {
    lat: i32, // scaled 위도
    lng: i32, // scaled 경도
}

fn to_points(flat: &[i32]) -> Vec<IntPoint> {
    let mut pts = Vec::with_capacity(flat.len() / 2);
    let mut i = 0;
    while i + 1 < flat.len() {
        pts.push(IntPoint {
            lat: flat[i],
            lng: flat[i + 1],
        });
        i += 2;
    }
    pts
}

fn points_to_flat(pts: &[IntPoint]) -> Vec<i32> {
    let mut out = Vec::with_capacity(pts.len() * 2);
    for p in pts {
        out.push(p.lat);
        out.push(p.lng);
    }
    out
}

fn int_to_deg(p: &IntPoint) -> (f64, f64) {
    let lat = p.lat as f64 / SCALE;
    let lng = p.lng as f64 / SCALE;
    (lat, lng)
}

fn haversine_distance_m(p1: &IntPoint, p2: &IntPoint) -> f64 {
    let (lat1_deg, lng1_deg) = int_to_deg(p1);
    let (lat2_deg, lng2_deg) = int_to_deg(p2);

    let lat1 = lat1_deg.to_radians();
    let lat2 = lat2_deg.to_radians();
    let dlat = lat2 - lat1;
    let dlng = (lng2_deg - lng1_deg).to_radians();

    let a = (dlat / 2.0).sin().powi(2)
        + lat1.cos() * lat2.cos() * (dlng / 2.0).sin().powi(2);
    let c = 2.0 * a.sqrt().asin();
    EARTH_RADIUS_M * c
}

/// 근사적인 평면 좌표 (equirectangular) 변환
fn to_xy_meters(p: &IntPoint, lat0_rad: f64) -> (f64, f64) {
    let (lat_deg, lng_deg) = int_to_deg(p);
    let lat = lat_deg.to_radians();
    let lng = lng_deg.to_radians();
    let x = EARTH_RADIUS_M * lng * lat0_rad.cos();
    let y = EARTH_RADIUS_M * lat;
    (x, y)
}

/// p에서 선분 (a-b)까지의 수직 거리 (m)
fn perpendicular_distance_m(p: &IntPoint, a: &IntPoint, b: &IntPoint) -> f64 {
    let (lat_a, _) = int_to_deg(a);
    let lat0_rad = lat_a.to_radians();

    let (ax, ay) = to_xy_meters(a, lat0_rad);
    let (bx, by) = to_xy_meters(b, lat0_rad);
    let (px, py) = to_xy_meters(p, lat0_rad);

    let vx = bx - ax;
    let vy = by - ay;
    let wx = px - ax;
    let wy = py - ay;

    let v_len2 = vx * vx + vy * vy;
    if v_len2 == 0.0 {
        // a와 b가 같으면, a와의 거리
        return ((px - ax).powi(2) + (py - ay).powi(2)).sqrt();
    }

    // 선분에 대한 투영 비율 t
    let t = (wx * vx + wy * vy) / v_len2;
    let t_clamped = t.clamp(0.0, 1.0);

    let proj_x = ax + t_clamped * vx;
    let proj_y = ay + t_clamped * vy;

    ((px - proj_x).powi(2) + (py - proj_y).powi(2)).sqrt()
}

/// RDP 재귀
fn rdp_recursive(points: &[IntPoint], first: usize, last: usize, eps_m: f64, keep: &mut [bool]) {
    if last <= first + 1 {
        return;
    }

    let a = points[first];
    let b = points[last];

    let mut max_dist = 0.0;
    let mut index = None;

    for i in (first + 1)..last {
        let d = perpendicular_distance_m(&points[i], &a, &b);
        if d > max_dist {
            max_dist = d;
            index = Some(i);
        }
    }

    if let Some(idx) = index {
        if max_dist > eps_m {
            keep[idx] = true;
            rdp_recursive(points, first, idx, eps_m, keep);
            rdp_recursive(points, idx, last, eps_m, keep);
        }
    }
}

/// RDP 궤적 압축
fn rdp_simplify(points: &[IntPoint], eps_m: f64) -> Vec<IntPoint> {
    let n = points.len();
    if n <= 2 {
        return points.to_vec();
    }

    let mut keep = vec![false; n];
    keep[0] = true;
    keep[n - 1] = true;

    rdp_recursive(points, 0, n - 1, eps_m, &mut keep);

    let mut out = Vec::new();
    for (i, p) in points.iter().enumerate() {
        if keep[i] {
            out.push(*p);
        }
    }
    out
}

/// 그리드 기반 클러스터링
/// 입력: [lat_i, lng_i, lat_i, lng_i, ...] (scaled int)
/// cell_size_m: 각 셀의 한 변 길이(m)
/// 출력: [cluster_lat_i, cluster_lng_i, count, ...] (lat/lng scaled int, count = i32)
fn grid_cluster(points: &[IntPoint], cell_size_m: f64) -> Vec<(IntPoint, i32)> {
    if points.is_empty() {
        return Vec::new();
    }

    // 기준 위도(평균)로 평면 근사
    let mut lat_sum = 0.0;
    for p in points {
        let (lat_deg, _) = int_to_deg(p);
        lat_sum += lat_deg;
    }
    let lat0_rad = (lat_sum / points.len() as f64).to_radians();

    #[derive(Hash, Eq, PartialEq, Debug, Clone, Copy)]
    struct CellKey {
        x: i32,
        y: i32,
    }

    #[derive(Debug)]
    struct Acc {
        sum_lat: i64,
        sum_lng: i64,
        count: i32,
    }

    let mut map: HashMap<CellKey, Acc> = HashMap::new();

    for p in points {
        let (x, y) = to_xy_meters(p, lat0_rad);
        let cx = (x / cell_size_m).floor() as i32;
        let cy = (y / cell_size_m).floor() as i32;
        let key = CellKey { x: cx, y: cy };

        map.entry(key)
            .and_modify(|acc| {
                acc.sum_lat += p.lat as i64;
                acc.sum_lng += p.lng as i64;
                acc.count += 1;
            })
            .or_insert(Acc {
                sum_lat: p.lat as i64,
                sum_lng: p.lng as i64,
                count: 1,
            });
    }

    let mut clusters = Vec::with_capacity(map.len());
    for (_key, acc) in map {
        let avg_lat = (acc.sum_lat / acc.count as i64) as i32;
        let avg_lng = (acc.sum_lng / acc.count as i64) as i32;
        clusters.push((
            IntPoint {
                lat: avg_lat,
                lng: avg_lng,
            },
            acc.count,
        ));
    }

    clusters
}

/// ===== WASM 바인딩 API =====

#[wasm_bindgen]
pub fn compress_trajectory(points_flat: &[i32], min_dist_meters: f64) -> Vec<i32> {
    let pts = to_points(points_flat);
    if pts.len() <= 2 {
        return points_flat.to_vec();
    }
    let simplified = rdp_simplify(&pts, min_dist_meters);
    points_to_flat(&simplified)
}

/// cluster_points 결과 형식:
/// [lat1, lng1, count1, lat2, lng2, count2, ...]
#[wasm_bindgen]
pub fn cluster_points(points_flat: &[i32], cell_size_m: f64) -> Vec<i32> {
    let pts = to_points(points_flat);
    let clusters = grid_cluster(&pts, cell_size_m);

    let mut out = Vec::with_capacity(clusters.len() * 3);
    for (p, count) in clusters {
        out.push(p.lat);
        out.push(p.lng);
        out.push(count);
    }
    out
}
