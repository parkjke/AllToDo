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

/// Greedy Radius-Based Clustering (Integer Math with Equirectangular Approx)
/// 입력: `points`: IntPoint 목록, `radius_int`: 클러스터 반경 (Scaled Int, e.g., 500m -> 500/1.1 * 100? No, 1 deg ≈ 111km. 1e5 scale. 1 unit ≈ 1.11m. So 500m ≈ 450 units.)
/// User will pass `cellSize` which is effectively `radius`.
fn radius_cluster(points: &[IntPoint], radius_int: i32) -> Vec<(IntPoint, i32)> {
    if points.is_empty() {
        return Vec::new();
    }

    let mut clusters: Vec<(IntPoint, i32)> = Vec::new(); // (Centroid, Count)
    let mut visited = vec![false; points.len()];
    let r_sq = (radius_int as i64).pow(2);

    // Greedy Approach: O(N*C) where C is number of clusters. Worst case O(N^2).
    // For 2000 points, 4M ops is fine in WASM.
    for i in 0..points.len() {
        if visited[i] {
            continue;
        }

        // Start new cluster with pivot
        visited[i] = true;
        let p1 = points[i];
        
        let mut sum_lat = p1.lat as i64;
        let mut sum_lng = p1.lng as i64;
        let mut count = 1;
        let mut member_indices = vec![i]; // To update centroid strictly? 
        // No, we can just accumulate sum for centroid calculation.
        
        // Calculate Cosine Factor for this latitude (Pivot)
        // Lat is scaled 1e5. radians = lat / 1e5 * pi / 180
        let lat_rad = (p1.lat as f64 / 100_000.0).to_radians();
        let cos_factor = lat_rad.cos(); 
        
        // Integer approximation for cos correction:
        // dx_corr = dx * cos_factor.
        // We do floating point mult for cos factor effectively, or we can scale cos factor.
        // Let's use float for the check locally, it's fast enough.
        // dist_sq = (dx * cos)^2 + dy^2
        
        for j in (i + 1)..points.len() {
            if visited[j] {
                continue;
            }
            
            let p2 = points[j];
            let dy = (p1.lat - p2.lat).abs() as i64;
            
            // Fast Rejection (Y-axis)
            if dy as i32 > radius_int {
                continue; 
            }
            
            let dx = (p1.lng - p2.lng).abs() as i64;
            // X-axis rough rejection
            // if dx as i32 > radius_int * 2 { continue; } // conservative
            
            // Accurate Check
            let dx_corr = (dx as f64 * cos_factor) as i64;
            
            let dist_sq = dx_corr * dx_corr + dy * dy;
            
            if dist_sq <= r_sq {
                visited[j] = true;
                sum_lat += p2.lat as i64;
                sum_lng += p2.lng as i64;
                count += 1;
            }
        }
        
        // Create Cluster
        // Centroid is average of members
        let avg_lat = (sum_lat / count as i64) as i32;
        let avg_lng = (sum_lng / count as i64) as i32;
        
        clusters.push((
            IntPoint { lat: avg_lat, lng: avg_lng },
            count
        ));
    }

    clusters
}

/// cluster_points 결과 형식:
/// [lat1, lng1, count1, lat2, lng2, count2, ...]
/// `radius_int`: 클러스터링 반경 (1e5 scale).
/// e.g., 300m -> ~270 units. WasmManager should pass converted int.
#[wasm_bindgen]
pub fn cluster_points(points_flat: &[i32], radius_int: i32) -> Vec<i32> {
    let pts = to_points(points_flat);
    
    // Sort logic? Greedy is sensitive to order.
    // Sorting by density or Y-axis can stabilize.
    // For now, respect input order (usually meaningful or random).
    // Native maps usually process high-priority first if sorted, but here we just cluster.
    
    let clusters = radius_cluster(&pts, radius_int);

    let mut out = Vec::with_capacity(clusters.len() * 3);
    for (p, count) in clusters {
        out.push(p.lat);
        out.push(p.lng);
        out.push(count);
    }
    out
}

/// RDP Trajectory Compression (Exposed to WASM)
/// 입력: `points_flat` [lat, lng, lat, lng...], `tolerance_m` (meters)
/// 출력: [lat, lng, lat, lng...]
#[wasm_bindgen]
pub fn compress_trajectory(points_flat: &[i32], tolerance_m: f64) -> Vec<i32> {
    let pts = to_points(points_flat);
    let simplified = rdp_simplify(&pts, tolerance_m);
    points_to_flat(&simplified)
}
